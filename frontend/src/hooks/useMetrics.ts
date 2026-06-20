import { useEffect, useState } from 'react';
import type { Metrics } from '../types';
import { API_BASE } from '../config';

const POLL_INTERVAL = 1000;

export function useMetrics() {
  const [metrics, setMetrics] = useState<Metrics>({
    lastLatencyUs: 0,
    avgLatencyUs: 0,
    tps: 0,
    openOrderCount: 0,
  });

  useEffect(() => {
    const fetchMetrics = () => {
      fetch(`${API_BASE}/metrics`)
        .then(r => r.json())
        .then(setMetrics)
        .catch(() => {});
    };

    fetchMetrics();
    const id = setInterval(fetchMetrics, POLL_INTERVAL);
    return () => clearInterval(id);
  }, []);

  return metrics;
}
