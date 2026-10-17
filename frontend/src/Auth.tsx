import React, { useState } from "react";
import { api } from "./api";
import type { User } from "./api";
import { Brand, Notice, Disclaimer } from "./ui";
import { message } from "./format";

export function Auth({ signedIn }: { signedIn: (u: User) => void }) {
  const [register, setRegister] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(e.currentTarget);
    try {
      signedIn(
        await api<User>(register ? "/auth/register" : "/auth/login", "POST", {
          email: data.get("email"),
          password: data.get("password"),
        }),
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-shell">
      <section className="auth-story">
        <Brand />
        <div>
          <p className="eyebrow">A CLEARER VIEW OF YOUR DECISIONS</p>
          <h1>
            Conviction starts
            <br />
            with understanding.
          </h1>
          <p className="lead">
            Explore the mechanics of investing in a deliberate, transparent
            simulation workspace.
          </p>
          <div className="principles">
            <div>
              <b>01</b>
              <span>
                Build a portfolio
                <small>Start with simulated USD capital.</small>
              </span>
            </div>
            <div>
              <b>02</b>
              <span>
                Make your decisions
                <small>Execute at known historical synthetic closes.</small>
              </span>
            </div>
            <div>
              <b>03</b>
              <span>
                Follow the evidence
                <small>Inspect every trade, holding, and cash movement.</small>
              </span>
            </div>
          </div>
        </div>
        <Disclaimer />
      </section>
      <main id="main" className="auth-form">
        <div className="form-wrap">
          <span className="badge">PHASE 1 · SIMULATION WORKSPACE</span>
          <h2>{register ? "Create your workspace" : "Welcome back"}</h2>
          <p className="muted">
            {register
              ? "A private space to explore portfolio decisions."
              : "Sign in to continue your investment simulation."}
          </p>
          <form onSubmit={(e) => void submit(e)}>
            <label>
              Email address
              <input
                name="email"
                type="email"
                autoComplete="email"
                maxLength={254}
                required
                placeholder="you@example.com"
              />
            </label>
            <label>
              Password
              <input
                name="password"
                type="password"
                autoComplete={register ? "new-password" : "current-password"}
                minLength={12}
                maxLength={72}
                required
                aria-describedby="password-help"
              />
            </label>
            <p id="password-help" className="help">
              12–72 characters. For registration, at most 72 UTF-8 bytes.
            </p>
            {error && <Notice error>{error}</Notice>}
            <button className="primary" disabled={busy}>
              {busy ? "Please wait…" : register ? "Create account" : "Sign in"}{" "}
              <span aria-hidden="true">↗</span>
            </button>
          </form>
          <p className="auth-switch">
            {register ? "Already have an account?" : "New to AEGIS?"}{" "}
            <button
              className="text-button"
              onClick={() => {
                setRegister(!register);
                setError("");
              }}
            >
              {register ? "Sign in" : "Create an account"}
            </button>
          </p>
          <p className="help">
            Session-based access. Your password is hashed; authentication tokens
            are never stored in browser local storage.
          </p>
        </div>
      </main>
    </div>
  );
}
