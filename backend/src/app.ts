import express, { type Express } from 'express';
import { OAuth2Client } from 'google-auth-library';
import { env } from './config/env';

export function createApp(): Express {
  const app = express();

  app.use(express.json({ limit: '16kb' }))

  const googleClient = new OAuth2Client();

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.get('/developer/name', (_req, res) => {
    res.json({
      firstName: 'Haonan',
      lastName: 'Bai',
    });
  });

  app.post('/auth/google', async (req, res) => {
    if (!env.googleClientId) {
      res.status(503).json({ error: 'Google login is not configured' });
      return;
    }

    const idToken: unknown = req.body?.idToken;

    if (typeof idToken !== 'string' || idToken.trim() === '') {
      res.status(400).json({ error: 'idToken is required' });
      return;
    }

    try {
      const ticket = await googleClient.verifyIdToken({
        idToken,
        audience: env.googleClientId,
      });

      const payload = ticket.getPayload();

      if (!payload?.sub) {
        res.status(401).json({ error: 'Invalid Google identity' });
        return;
      }

      res.json({
        user: {
          id: payload.sub,
          firstName: payload.given_name ?? null,
          lastName: payload.family_name ?? null,
        },
      });
    } catch {
      res.status(401).json({ error: 'Google token verification failed' });
    }
  });

  app.get('/server/time', (_req, res) => {
    const now = new Date();

    res.set('Cache-Control', 'no-store');
    res.json({
      time: formatLocalTime(now),
    });
  });

  app.use((_req, res) => {
    res.status(404).json({ error: 'Not Found' });
  });

  return app;
}

function formatLocalTime(date: Date): string {
  const pad2 = (value: number): string =>
    String(value).padStart(2, '0');

  const time = [
    date.getHours(),
    date.getMinutes(),
    date.getSeconds(),
  ].map(pad2).join(':');

  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? '+' : '-';
  const absoluteOffset = Math.abs(offsetMinutes);

  const offsetHours = pad2(Math.floor(absoluteOffset / 60));
  const offsetRemainder = pad2(absoluteOffset % 60);

  return `${time} GMT${sign}${offsetHours}:${offsetRemainder}`;
}