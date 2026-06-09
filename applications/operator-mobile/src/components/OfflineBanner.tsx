import { useEffect, useState, useRef } from 'react'
import { View, Text, StyleSheet, Animated } from 'react-native'
import NetInfo from '@react-native-community/netinfo'
import { syncQueue } from '../services/SyncQueue'

export default function OfflineBanner() {
  const [isOnline, setIsOnline] = useState(true)
  const [pendingCount, setPendingCount] = useState(0)
  const opacity = useRef(new Animated.Value(0)).current
  const showBanner = !isOnline || pendingCount > 0

  useEffect(() => {
    const unsubNetInfo = NetInfo.addEventListener((state) => {
      setIsOnline(state.isConnected === true)
    })
    const unsubQueue = syncQueue.onStatusChange(setPendingCount)
    syncQueue.getPendingCount().then(setPendingCount)
    return () => {
      unsubNetInfo()
      unsubQueue()
    }
  }, [])

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: showBanner ? 1 : 0,
      duration: 300,
      useNativeDriver: true,
    }).start()
  }, [showBanner, opacity])

  // Keep the Animated.View mounted so fade-out animation can play
  return (
    <Animated.View style={[styles.banner, { opacity }]} pointerEvents={showBanner ? 'auto' : 'none'}>
      <View style={[styles.dot, isOnline ? styles.dotSyncing : styles.dotOffline]} />
      <Text style={styles.text}>
        {isOnline
          ? `Đang đồng bộ ${pendingCount} hành động...`
          : `Ngoại tuyến${pendingCount > 0 ? ` · ${pendingCount} hành động chờ` : ''}`}
      </Text>
    </Animated.View>
  )
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#333',
    paddingHorizontal: 16,
    paddingVertical: 8,
    gap: 8,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  dotOffline: {
    backgroundColor: '#F44336',
  },
  dotSyncing: {
    backgroundColor: '#FF9800',
  },
  text: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '500',
  },
})
