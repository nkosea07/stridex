import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Linking, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';
import { useTranslation } from 'react-i18next';
import { mapConfig } from '@/lib/map/config';
import { buildLeafletHtml, injectionFor, isAllowedNavigation, parseLeafletMessage, type LeafletInbound } from '@/lib/map/leafletHtml';
import { diffLines, isEmptyPatch, type LineState } from '@/lib/map/scenePatch';
import { NO_CIRCLES, NO_LINES, NO_PINS, type RaceMapProps } from '@/lib/map/types';

const WEB_BASE_URL = process.env.EXPO_PUBLIC_WEB_URL?.startsWith('https://')
  ? process.env.EXPO_PUBLIC_WEB_URL
  : 'https://localhost/';

export function LeafletRaceMap({
  initialRegion,
  lines = NO_LINES,
  pins = NO_PINS,
  circles = NO_CIRCLES,
  userLocation = null,
  followUser = false,
  recenterKey = 0,
  onPinPress,
  accessibilityLabel,
  theme,
  height,
  fill,
}: RaceMapProps) {
  const { t } = useTranslation();
  const webView = useRef<WebView>(null);
  const [instance, setInstance] = useState(0);
  const [generation, setGeneration] = useState(0);
  const [failed, setFailed] = useState(false);
  const [region] = useState(initialRegion);
  const lineState = useRef<LineState>(new Map());
  const lastRecenter = useRef(recenterKey);
  const zoomIn = t('mobile:map.zoomIn');
  const zoomOut = t('mobile:map.zoomOut');
  const html = useMemo(() => buildLeafletHtml(mapConfig, region, { zoomIn, zoomOut }), [region, zoomIn, zoomOut]);

  const send = useCallback((message: LeafletInbound) => {
    webView.current?.injectJavaScript(injectionFor(message));
  }, []);

  const reload = useCallback(() => {
    setGeneration(0);
    setFailed(false);
    lineState.current = new Map();
    setInstance((value) => value + 1);
  }, []);

  useEffect(() => {
    if (!generation) return;
    const { patch, state } = diffLines(lineState.current, lines);
    lineState.current = state;
    if (!isEmptyPatch(patch)) send({ type: 'lines', patch });
  }, [generation, lines, send]);

  useEffect(() => {
    if (generation) send({ type: 'pins', pins, theme: { primary: theme.primary, surface: theme.surface } });
  }, [generation, pins, theme.primary, theme.surface, send]);

  useEffect(() => {
    if (generation) send({ type: 'circles', circles });
  }, [generation, circles, send]);

  useEffect(() => {
    if (generation) send({ type: 'user', location: userLocation, follow: followUser, color: theme.userLocation });
  }, [generation, userLocation, followUser, theme.userLocation, send]);

  useEffect(() => {
    if (!generation || recenterKey === lastRecenter.current) return;
    lastRecenter.current = recenterKey;
    send({ type: 'recenter' });
  }, [generation, recenterKey, send]);

  const onMessage = useCallback((event: WebViewMessageEvent) => {
    const message = parseLeafletMessage(event.nativeEvent.data);
    if (!message) return;
    if (message.type === 'ready') {
      lineState.current = new Map();
      setGeneration((value) => value + 1);
    } else if (message.type === 'pinPress') {
      onPinPress?.(message.id);
    } else if (message.type === 'openUrl') {
      Linking.openURL(message.url).catch(() => undefined);
    } else if (message.type === 'error') {
      setFailed(true);
    }
  }, [onPinPress]);

  const frame = [styles.container, fill ? styles.fill : { height: height ?? 320 }];

  if (failed) {
    return (
      <View style={[frame, styles.fallback, { backgroundColor: theme.surface }]} accessibilityLiveRegion="polite">
        <Text style={[styles.fallbackText, { color: theme.text }]}>{t('mobile:map.unavailable')}</Text>
        <TouchableOpacity onPress={reload} accessibilityRole="button" style={styles.retry}>
          <Text style={[styles.retryText, { color: theme.primary }]}>{t('mobile:map.retry')}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={frame} accessible accessibilityRole="image" accessibilityLabel={accessibilityLabel}>
      <WebView
        key={instance}
        ref={webView}
        source={{ html, baseUrl: WEB_BASE_URL }}
        originWhitelist={['https://*', 'about:*']}
        onMessage={onMessage}
        onError={() => setFailed(true)}
        onContentProcessDidTerminate={reload}
        onRenderProcessGone={reload}
        onShouldStartLoadWithRequest={(request) => isAllowedNavigation(request.url, WEB_BASE_URL)}
        javaScriptEnabled
        domStorageEnabled={false}
        allowFileAccess={false}
        allowsLinkPreview={false}
        setSupportMultipleWindows={false}
        mixedContentMode="never"
        nestedScrollEnabled
        scrollEnabled={false}
        overScrollMode="never"
        style={styles.webView}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { overflow: 'hidden' },
  fill: { flex: 1 },
  webView: { flex: 1, backgroundColor: 'transparent' },
  fallback: { alignItems: 'center', justifyContent: 'center', padding: 16, gap: 8 },
  fallbackText: { fontSize: 14, textAlign: 'center' },
  retry: { paddingHorizontal: 16, paddingVertical: 10 },
  retryText: { fontSize: 15, fontWeight: '600' },
});
