FROM python:3.12-slim
WORKDIR /app
COPY _lib /app/demos/_lib
COPY ai-triage-demo /app/demos/ai-triage-demo
WORKDIR /app/demos/ai-triage-demo
ENV AI_BRIDGE_PORT=8096
EXPOSE 8096
CMD ["python", "app.py"]
