# Build the React/Vite client
FROM node:22-bookworm-slim AS builder
WORKDIR /app

COPY src/main/webapp/package*.json ./
RUN npm ci

COPY src/main/webapp .

ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV NODE_ENV=production

RUN npm run build

# Serve the production build with nginx
FROM nginx:1.27-alpine AS runner

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
