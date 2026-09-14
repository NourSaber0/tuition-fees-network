import type { Metadata } from "next";
import { AuthProvider } from "@tuition/api-client";
import { ToastProvider } from "@tuition/ui";
import "./globals.css";

export const metadata: Metadata = {
  title: "CIB Tuition Network Portal",
  description: "Bank Back-Office and School Portal - one sign-in, role-routed.",
  other: {
    "darkreader-lock": "true",
  },
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="h-full antialiased" suppressHydrationWarning>
      <head>
        <meta name="darkreader-lock" content="true" />
      </head>
      <body className="min-h-full flex flex-col" suppressHydrationWarning>
        <AuthProvider baseUrl={API_BASE_URL}>
          <ToastProvider>{children}</ToastProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
