import { createApp } from './app';
import { env } from './config/env';
import WebSocket, { WebSocketServer } from 'ws';

const app = createApp();

const server = app.listen(env.port, () => {
  console.log(`Server listening on port ${env.port}`);
});

const pixelServer = new WebSocketServer({
  server,
  path: '/pixels',
});

pixelServer.on('connection', (client) => {
  console.log('Pixel client connected');

  const upstream = new WebSocket('wss://8.229.22.124', {
    handshakeTimeout: 10_000,
  });

  upstream.on('open', () => {
    console.log('Connected to course pixel server');
  });

  upstream.on('message', (data, isBinary) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(data, { binary: isBinary });
    }
  });

  upstream.on('error', (error) => {
    console.error('Course WebSocket error:', error.message);
    client.close(1011, 'Course server connection failed');
  });

  upstream.on('close', () => {
    if (client.readyState === WebSocket.OPEN) {
      client.close(1011, 'Course server disconnected');
    }
  });

  client.on('close', () => {
    console.log('Pixel client disconnected');
    upstream.terminate();
  });

  client.on('error', (error) => {
    console.error('Pixel client error:', error.message);
    upstream.terminate();
    client.terminate();
  });
});

pixelServer.on('error', (error) => {
  console.error('Pixel WebSocket server error:', error.message);
});

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    for (const client of pixelServer.clients) {
      client.terminate();
    }

    pixelServer.close();

    server.close(() => {
      process.exit(0);
    });
  });
}