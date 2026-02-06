# Stage 1: Build
FROM node:20-alpine AS build
WORKDIR /app

# Only copy lockfiles first to leverage Docker cache
COPY package*.json ./
RUN npm ci

# Copy the rest and build
COPY . .
RUN npm run build

# Stage 2: Production (Secure & Slim)
# ... (Stage 1 remains the same)

# Stage 2: Production
FROM nginx:alpine
RUN touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid /var/cache/nginx /var/log/nginx /etc/nginx/conf.d

# Move to 8080 to allow non-root execution
USER nginx

COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
# Ensure your local nginx.conf also listens on 8080!
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]