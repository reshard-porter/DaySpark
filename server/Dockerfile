FROM node:22-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev || npm install --omit=dev
COPY src ./src
RUN mkdir -p /app/data && chown node /app/data
ENV PORT=8080
USER node
CMD ["node", "src/index.js"]
