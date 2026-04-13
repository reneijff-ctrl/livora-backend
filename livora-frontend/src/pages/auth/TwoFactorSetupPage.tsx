import { useEffect, useState } from "react";
import QRCode from "react-qr-code";
import apiClient from "@/api/apiClient";
import { authStore } from "@/store/authStore";
import { useNavigate } from "react-router-dom";

export default function TwoFactorSetupPage() {
  const [qrUrl, setQrUrl] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState("");

  const navigate = useNavigate();
  const auth = authStore;

  useEffect(() => {
    loadSetup();
  }, []);

  const loadSetup = async () => {
    try {
      const res = await apiClient.post("/api/auth/2fa/setup");
      setQrUrl(res.data.qrUrl);
      setSecret(res.data.secret);
    } catch (e) {
      setError("Failed to load 2FA setup");
    }
  };

  const handleEnable = async () => {
    try {
      await apiClient.post("/api/auth/2fa/enable", null, {
        params: { code: Number(code) },
      });

      await auth.refresh();

      navigate("/admin");
    } catch (e) {
      setError("Invalid code");
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-black text-white">
      <div className="bg-zinc-900 p-6 rounded-xl w-full max-w-md">
        <h1 className="text-xl font-bold mb-4">Setup 2FA</h1>

        {qrUrl && (
          <div className="flex justify-center mb-4">
            <QRCode value={qrUrl} />
          </div>
        )}

        {secret && (
          <p className="text-xs text-gray-400 mb-4 break-all">
            Manual code: {secret}
          </p>
        )}

        <input
          type="text"
          placeholder="Enter 6-digit code"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          className="w-full p-2 rounded bg-zinc-800 mb-3"
        />

        {error && <p className="text-red-500 text-sm mb-2">{error}</p>}

        <button
          onClick={handleEnable}
          className="w-full bg-purple-600 py-2 rounded"
        >
          Enable 2FA
        </button>
      </div>
    </div>
  );
}
