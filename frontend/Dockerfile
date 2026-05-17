# Build stage
FROM node:26-alpine AS build
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source
COPY . .

# Build the application
RUN npm run build

# Production stage
FROM nginxinc/nginx-unprivileged:alpine
WORKDIR /usr/share/nginx/html

# Copy built assets
COPY --from=build /app/dist .

# Copy nginx config to unprivileged path
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080 || exit 1

CMD ["nginx", "-g", "daemon off;"]
