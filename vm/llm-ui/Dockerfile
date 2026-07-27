FROM node:22-bookworm-slim

WORKDIR /app
COPY package.json ./
COPY server.js ./server.js
COPY public ./public
COPY scripts ./scripts

ENV NODE_ENV=production
EXPOSE 8787
CMD ["node", "server.js"]
