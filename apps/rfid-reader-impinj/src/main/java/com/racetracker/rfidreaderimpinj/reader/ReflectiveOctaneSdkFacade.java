package com.racetracker.rfidreaderimpinj.reader;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.model.NormalizedRead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@Component
public class ReflectiveOctaneSdkFacade implements OctaneSdkFacade {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveOctaneSdkFacade.class);

    private Object readerInstance;
    private ClassLoader sdkClassLoader;

    @Override
    public void connectAndStart(
        ImpinjReaderProperties.Reader readerProperties,
        Consumer<NormalizedRead> readConsumer,
        Runnable keepaliveListener,
        Consumer<String> disconnectListener
    ) throws Exception {
        sdkClassLoader = resolveClassLoader(readerProperties.getSdkDir());
        Class<?> readerClass = sdkClassLoader.loadClass("com.impinj.octane.ImpinjReader");
        readerInstance = readerClass.getConstructor().newInstance();

        registerListenerIfPresent(readerClass, "setTagReportListener", "com.impinj.octane.TagReportListener",
            (method, args) -> handleTagReport(args, readConsumer));
        registerListenerIfPresent(readerClass, "setKeepaliveListener", "com.impinj.octane.KeepaliveListener",
            (method, args) -> keepaliveListener.run());
        registerListenerIfPresent(readerClass, "setConnectionLostListener", "com.impinj.octane.ConnectionLostListener",
            (method, args) -> disconnectListener.accept("Octane connection lost callback received"));

        invoke(readerInstance, "connect", readerProperties.getHost());
        Object settings = invoke(readerInstance, "queryDefaultSettings");
        applySettings(readerProperties, settings);
        invoke(readerInstance, "applySettings", settings);
        invoke(readerInstance, "start");
    }

    @Override
    public boolean isConnected() {
        if (readerInstance == null) {
            return false;
        }
        try {
            Object connected = invoke(readerInstance, "isConnected");
            return connected instanceof Boolean value ? value : true;
        } catch (Exception ignored) {
            // Treat a failed status probe as disconnected so the reconnect loop runs.
            return false;
        }
    }

    @Override
    public void stop() throws Exception {
        if (readerInstance == null) {
            return;
        }

        try {
            invoke(readerInstance, "stop");
        } catch (Exception error) {
            log.debug("Octane stop failed: {}", error.getMessage());
        }
        try {
            invoke(readerInstance, "disconnect");
        } finally {
            readerInstance = null;
        }
    }

    private void applySettings(ImpinjReaderProperties.Reader readerProperties, Object settings) throws Exception {
        Object report = invoke(settings, "getReport");
        invokeIfPresent(report, "setIncludeAntennaPortNumber", true);
        invokeIfPresent(report, "setIncludePeakRssi", true);
        setEnumIfPresent(report, "setMode", "com.impinj.octane.ReportMode", "Individual");

        Object keepalives = invokeIfPresent(settings, "getKeepalives");
        if (keepalives != null) {
            invokeIfPresent(keepalives, "setEnable", true);
            invokeIfPresent(keepalives, "setPeriodInMs", readerProperties.getKeepaliveIntervalMs());
        }

        setEnumIfPresent(settings, "setSearchMode", "com.impinj.octane.SearchMode", readerProperties.getSearchMode());
        invokeIfPresent(settings, "setSession", readerProperties.getSession());

        Object antennas = invokeIfPresent(settings, "getAntennas");
        if (antennas == null) {
            return;
        }
        invokeIfPresent(antennas, "disableAll");
        for (Integer antennaPort : normalizeAntennaPorts(readerProperties.getEnabledAntennas())) {
            Object antenna = invoke(antennas, "getAntenna", antennaPort.shortValue());
            invokeIfPresent(antenna, "setIsEnabled", true);
            if (readerProperties.getTxPowerDbM() != null) {
                invokeIfPresent(antenna, "setTxPowerInDbm", readerProperties.getTxPowerDbM());
            }
            if (readerProperties.getRxSensitivityDbM() != null) {
                invokeIfPresent(antenna, "setRxSensitivityInDbm", readerProperties.getRxSensitivityDbM());
            }
        }
    }

    private void handleTagReport(Object[] args, Consumer<NormalizedRead> readConsumer) {
        if (args == null || args.length < 2 || args[1] == null) {
            return;
        }
        Object report = args[1];
        try {
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) invoke(report, "getTags");
            for (Object tag : tags) {
                String epc = stringifyEpc(invokeIfPresent(tag, "getEpc"));
                if (epc == null || epc.isBlank()) {
                    continue;
                }
                String antenna = null;
                Object antennaPort = invokeIfPresent(tag, "getAntennaPortNumber");
                if (antennaPort != null) {
                    antenna = "A" + antennaPort;
                }
                BigDecimal rssi = null;
                Object peakRssi = invokeIfPresent(tag, "getPeakRssiInDbm");
                if (peakRssi instanceof Number value) {
                    rssi = BigDecimal.valueOf(value.doubleValue());
                }
                OffsetDateTime readAt = OffsetDateTime.now();
                Object lastSeenTime = invokeIfPresent(tag, "getLastSeenTime");
                if (lastSeenTime instanceof OffsetDateTime offsetDateTime) {
                    readAt = offsetDateTime;
                }
                readConsumer.accept(new NormalizedRead(
                    epc + "-" + System.nanoTime(),
                    epc,
                    readAt,
                    antenna,
                    rssi,
                    "{\"source\":\"impinj-octane\"}"
                ));
            }
        } catch (Exception error) {
            log.warn("Failed to translate Octane tag report: {}", error.getMessage());
        }
    }

    private String stringifyEpc(Object epcObject) throws Exception {
        if (epcObject == null) {
            return null;
        }
        try {
            Object hex = invoke(epcObject, "toHexString");
            return hex != null ? hex.toString() : null;
        } catch (Exception ignored) {
            return epcObject.toString();
        }
    }

    private void registerListenerIfPresent(
        Class<?> readerClass,
        String setterName,
        String listenerClassName,
        ListenerInvocation listenerInvocation
    ) throws Exception {
        Method setter = findMethod(readerClass, setterName, 1);
        if (setter == null) {
            return;
        }
        Class<?> listenerClass = sdkClassLoader.loadClass(listenerClassName);
        Object proxy = Proxy.newProxyInstance(
            sdkClassLoader,
            new Class<?>[] { listenerClass },
            (proxyInstance, method, args) -> {
                listenerInvocation.invoke(method, args);
                return null;
            }
        );
        setter.invoke(readerInstance, proxy);
    }

    private ClassLoader resolveClassLoader(String sdkDir) throws Exception {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            contextClassLoader.loadClass("com.impinj.octane.ImpinjReader");
            return contextClassLoader;
        } catch (ClassNotFoundException ignored) {
            // Fall through to optional external SDK dir.
        }

        if (sdkDir == null || sdkDir.isBlank()) {
            throw new IllegalStateException("Impinj Octane SDK not found on classpath and IMPINJ_SDK_DIR is not configured");
        }

        List<URL> urls = new ArrayList<>();
        try (var paths = Files.list(Path.of(sdkDir))) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> {
                    try {
                        urls.add(path.toUri().toURL());
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
        }
        if (urls.isEmpty()) {
            throw new IllegalStateException("No Octane SDK jars found in " + sdkDir);
        }
        return new URLClassLoader(urls.toArray(URL[]::new), contextClassLoader);
    }

    private List<Integer> normalizeAntennaPorts(List<Integer> ports) {
        if (ports == null || ports.isEmpty()) {
            return List.of(1);
        }
        return ports.stream().filter(port -> port != null && port > 0).distinct().toList();
    }

    private Object invoke(Object target, String methodName, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, args.length);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "." + methodName + "(" + args.length + ")");
        }
        try {
            return method.invoke(target, adaptArgs(method.getParameterTypes(), args));
        } catch (InvocationTargetException error) {
            Throwable cause = error.getTargetException();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw error;
        }
    }

    private Object invokeIfPresent(Object target, String methodName, Object... args) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), methodName, args.length);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, adaptArgs(method.getParameterTypes(), args));
        } catch (InvocationTargetException error) {
            Throwable cause = error.getTargetException();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw error;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void setEnumIfPresent(Object target, String methodName, String enumClassName, String enumName) throws Exception {
        if (target == null || enumName == null || enumName.isBlank()) {
            return;
        }
        Method method = findMethod(target.getClass(), methodName, 1);
        if (method == null) {
            return;
        }
        Class<?> enumClass = sdkClassLoader.loadClass(enumClassName);
        Object enumValue = Enum.valueOf((Class<? extends Enum>) enumClass, enumName);
        method.invoke(target, enumValue);
    }

    private Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private Object[] adaptArgs(Class<?>[] parameterTypes, Object[] args) {
        Object[] adapted = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            adapted[index] = adaptArg(parameterTypes[index], args[index]);
        }
        return adapted;
    }

    private Object adaptArg(Class<?> parameterType, Object value) {
        if (value == null) {
            return null;
        }
        if (!parameterType.isPrimitive()) {
            if (parameterType == Short.class && value instanceof Number number) {
                return number.shortValue();
            }
            if (parameterType == Integer.class && value instanceof Number number) {
                return number.intValue();
            }
            if (parameterType == Double.class && value instanceof Number number) {
                return number.doubleValue();
            }
            if (parameterType == Boolean.class && value instanceof Boolean bool) {
                return bool;
            }
            return value;
        }
        if (parameterType == short.class && value instanceof Number number) {
            return number.shortValue();
        }
        if (parameterType == int.class && value instanceof Number number) {
            return number.intValue();
        }
        if (parameterType == long.class && value instanceof Number number) {
            return number.longValue();
        }
        if (parameterType == double.class && value instanceof Number number) {
            return number.doubleValue();
        }
        if (parameterType == boolean.class && value instanceof Boolean bool) {
            return bool;
        }
        return value;
    }

    @FunctionalInterface
    private interface ListenerInvocation {
        void invoke(Method method, Object[] args) throws Exception;
    }
}
