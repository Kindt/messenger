FROM python:3.12-slim
WORKDIR /app
COPY app.py .
ENV OCR_WORKER_PORT=8095
EXPOSE 8095
CMD ["python", "app.py"]
