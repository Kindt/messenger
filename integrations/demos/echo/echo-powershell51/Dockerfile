FROM mcr.microsoft.com/powershell:lts-7.4-ubuntu-22.04
WORKDIR /app
COPY server.ps1 .
EXPOSE 8087
CMD ["pwsh", "-File", "/app/server.ps1"]
