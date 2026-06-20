FROM python:3.12-alpine
WORKDIR /app
COPY dlp-mock/app.py /app/app.py
ENV DLP_MOCK_PORT=8098
EXPOSE 8098
CMD ["python", "/app/app.py"]
