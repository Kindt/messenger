"""Parity tests: generate-web-client-env.py vs WebClientEnvServletTest cases."""

from scripts.generate_web_client_env import build_env_script_body


def test_default_ws_and_null_ice_when_unset():
    body = build_env_script_body(lambda _k: None)
    assert (
        body
        == 'window.__WEB_CLIENT__ = { wsUrl: "ws://127.0.0.1:8081/ws", iceServersJson: null, vapidPublicKey: null, disableServiceWorker: false };\n'
    )


def test_trims_trailing_slash_on_ws_url():
    env = {"WEB_CLIENT_WS_PUBLIC_URL": "ws://lb.example/ws/"}
    body = build_env_script_body(env.get)
    assert 'wsUrl: "ws://lb.example/ws"' in body
    assert "iceServersJson: null" in body


def test_ice_servers_json_quoted_for_client_parse():
    env = {
        "WEB_CLIENT_WS_PUBLIC_URL": "ws://x/ws",
        "WEB_CLIENT_RTC_ICE_SERVERS": '[{"urls":"stun:custom:19302"}]',
    }
    body = build_env_script_body(env.get)
    assert 'wsUrl: "ws://x/ws"' in body
    assert 'iceServersJson: "[{\\"urls\\":\\"stun:custom:19302\\"}]"' in body


def test_vapid_public_key_quoted_when_set():
    env = {
        "WEB_CLIENT_WS_PUBLIC_URL": "ws://h/ws",
        "WEB_CLIENT_VAPID_PUBLIC_KEY": "BKx-example",
    }
    body = build_env_script_body(env.get)
    assert 'vapidPublicKey: "BKx-example"' in body


def test_empty_ice_env_becomes_null_in_script():
    env = {
        "WEB_CLIENT_WS_PUBLIC_URL": "ws://h/ws",
        "WEB_CLIENT_RTC_ICE_SERVERS": "  ",
    }
    body = build_env_script_body(env.get)
    assert "iceServersJson: null" in body
