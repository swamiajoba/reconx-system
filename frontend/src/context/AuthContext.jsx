// AuthContext used by withAuth HOC; JWT persisted in memory
// (refresh path lives in HttpOnly cookie — out of scope for this trainer copy).
import React, { createContext, useContext, useState } from 'react';

const AuthContext = createContext({ user: null, login: () => {}, logout: () => {} });

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const t = sessionStorage.getItem('reconx-token');
    const r = sessionStorage.getItem('reconx-role');
    return t ? { token: t, role: r } : null;
  });

  const login = (token, role) => {
    sessionStorage.setItem('reconx-token', token);
    sessionStorage.setItem('reconx-role', role);
    setUser({ token, role });
  };

  const logout = () => {
    sessionStorage.removeItem('reconx-token');
    sessionStorage.removeItem('reconx-role');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
