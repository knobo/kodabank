FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_BFF_URL
ENV VITE_BFF_URL=${VITE_BFF_URL}
RUN npm run build

FROM node:22-slim
WORKDIR /app
COPY --from=build /app/package.json /app/package-lock.json ./
# runtime needs no devDependencies and no npm itself (both only add CVEs)
RUN npm ci --omit=dev && npm cache clean --force \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx
COPY --from=build /app/dist dist
COPY --from=build /app/start.mjs .
EXPOSE 3000
ENV PORT=3000
CMD ["node", "start.mjs"]
