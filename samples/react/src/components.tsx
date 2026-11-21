import { useEffect, useState, type ReactNode } from "react";

export function Button({ children, kind = "primary", disabled = false }: { children: ReactNode; kind?: "primary" | "secondary" | "danger"; disabled?: boolean }) {
  return (
    <button className="button" data-kind={kind} disabled={disabled}>
      {children}
    </button>
  );
}

export function Badge({ count }: { count: number }) {
  return <span className="badge">{count > 99 ? "99+" : count}</span>;
}

export function Card({ title, body }: { title: string; body: string }) {
  return (
    <div className="card">
      <div className="title">{title}</div>
      <div className="muted">{body}</div>
      <div className="row">
        <Button>Open</Button>
        <Button kind="secondary">Later</Button>
      </div>
    </div>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint: string; action: string }) {
  return (
    <div className="empty">
      <div className="empty-art" />
      <div className="title">{title}</div>
      <div className="muted">{hint}</div>
      <div data-vcase-region="action">
        <Button>{action}</Button>
      </div>
    </div>
  );
}

/** Fills over [durationMs], driven by requestAnimationFrame and performance.now (not CSS). */
export function Progress({ durationMs }: { durationMs: number }) {
  const [fraction, setFraction] = useState(0);
  useEffect(() => {
    const start = performance.now();
    let frame = requestAnimationFrame(function tick(now) {
      const f = Math.min(1, (now - start) / durationMs);
      setFraction(f);
      if (f < 1) frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  }, [durationMs]);
  return (
    <div className="progress">
      <div style={{ width: `${Math.round(fraction * 240)}px` }} />
    </div>
  );
}
