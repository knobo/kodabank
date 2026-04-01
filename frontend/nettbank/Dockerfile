FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_BFF_URL
ENV VITE_BFF_URL=${VITE_BFF_URL}
RUN npx vite build

FROM node:22-slim
WORKDIR /app
COPY --from=build /app/.output .output
COPY --from=build /app/package.json .
EXPOSE 3000
ENV PORT=3000
CMD ["node", ".output/server/index.mjs"]
