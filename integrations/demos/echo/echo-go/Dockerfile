FROM golang:1.22-alpine AS build
WORKDIR /src
COPY main.go .
RUN go build -o /echo-go main.go

FROM alpine:3.20
COPY --from=build /echo-go /echo-go
EXPOSE 8080
ENTRYPOINT ["/echo-go"]
