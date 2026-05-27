# Acceptance report template (spec 003)

Run after Ansible deploy:

```bash
cd deploy/ansible
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml -e run_smoke=true
```

Record results:

| Step | Status | Notes |
|------|--------|-------|
| Ansible ci-local | | |
| wait-stack-ready | | |
| smoke-ready | | |
| smoke-auth | | |
| smoke-messaging-e2e | | |
| smoke-web-parity-api | | |

Date: ___________  
Environment: ___________
