// useTradeStream() — SSE subscription returning live trades.
import { useEffect, useState } from 'react';

export function useTradeStream(url = '/api/v1/trades/stream') {
  const [trades, setTrades] = useState([]);
  const [isConnected, setConnected] = useState(false);

  useEffect(() => {
    const sse = new EventSource(url);
    sse.onopen = () => setConnected(true);
    sse.onmessage = (e) => {
      try {
        const t = JSON.parse(e.data);
        setTrades((prev) => [t, ...prev].slice(0, 200));
      } catch { /* ignore malformed payload */ }
    };
    sse.onerror = () => setConnected(false);
    return () => sse.close();
  }, [url]);

  return { trades, isConnected };
}
