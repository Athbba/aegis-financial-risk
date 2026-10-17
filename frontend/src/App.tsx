import { useEffect, useState } from "react";
import { api, ApiError, clearCsrf } from "./api";
import type { User } from "./api";
import { Brand, Notice } from "./ui";
import { message } from "./format";
import { Auth } from "./Auth";
import { Dashboard } from "./Dashboard";

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(true);
  const [initialError, setInitialError] = useState("");
  async function check() {
    setChecking(true);
    setInitialError("");
    try {
      setUser(await api<User>("/auth/me"));
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401))
        setInitialError(message(e));
    } finally {
      setChecking(false);
    }
  }
  useEffect(() => {
    void check();
  }, []);
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      {checking ? (
        <main id="main" className="center">
          <Brand />
          <p role="status">Opening your workspace…</p>
        </main>
      ) : initialError ? (
        <main id="main" className="center">
          <Brand />
          <Notice error>{initialError}</Notice>
          <button onClick={() => void check()}>Retry connection</button>
        </main>
      ) : user ? (
        <Dashboard
          user={user}
          signedOut={() => {
            setUser(null);
            clearCsrf();
          }}
        />
      ) : (
        <Auth
          signedIn={(u) => {
            clearCsrf();
            setUser(u);
          }}
        />
      )}
    </>
  );
}
