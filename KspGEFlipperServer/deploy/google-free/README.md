# Google Compute Engine free-tier deployment

This deployment runs the standalone Java server on an Ubuntu 24.04 `e2-micro` VM and publishes it only through Tailscale Funnel. The application listens on `127.0.0.1:8181`; do **not** create a Google firewall rule for TCP 8181 or install PostgreSQL.

## Prerequisites

Create an eligible VM manually in Google Cloud: `us-east1`, E2 `e2-micro`, Ubuntu 24.04 LTS, and a 20 GB **standard persistent disk**. This repository does not create Google resources. Google pricing/eligibility and Tailscale plan availability can change, so confirm them in your own account before creating resources.

You need SSH access and a Tailscale account. Funnel needs MagicDNS, HTTPS, and a tailnet policy that allows Funnel; the first enablement can require approval by the tailnet administrator. Refer to the [Google Compute Engine free-tier documentation](https://cloud.google.com/free/docs/free-cloud-features#compute), [Tailscale Linux install guide](https://tailscale.com/docs/install/linux), and [Tailscale Funnel CLI reference](https://tailscale.com/docs/reference/tailscale-cli/funnel).

## Initial setup

Clone the repository on the VM, then run the setup script. It creates a separate deployment clone under `/opt/ksp-ge-flipper/source`, a `kspge` system user, 2 GB swap, restricted persistent storage, a generated API key in `/etc/ksp-ge-flipper.env`, a capped systemd service, and bounded journald storage.

```bash
git clone https://github.com/KSPOG/ksppluginsrelease.git
cd ksppluginsrelease/KspGEFlipperServer/deploy/google-free
sudo ./setup-vm.sh
```

If you have a pre-authorized Tailscale auth key, provide it only for the command that runs setup; it is not saved by the script:

```bash
sudo TAILSCALE_AUTH_KEY='tskey-...' ./setup-vm.sh
```

Without that key, `tailscale up` prints an interactive login URL. Complete that login, then allow the script to continue. The script configures persistent Funnel forwarding to `http://127.0.0.1:8181`, verifies the local health endpoint, and then verifies the Funnel URL. It never prints the API key.

Retrieve the endpoint and API key only on the VM:

```bash
sudo tailscale funnel status
sudo grep '^KSP_API_KEY=' /etc/ksp-ge-flipper.env
```

Configure the GE Flipper plugin in remote/server mode with the HTTPS `.ts.net` endpoint and that API key. Do not use `http://127.0.0.1:8181` on the Windows PC: that would point back to the PC, not the VM.

## Operations

```bash
# Update source, build, self-test, replace the JAR, and roll back on failed checks.
sudo ./update.sh

# Create a dated archive of /var/lib/ksp-ge-flipper; retain seven days.
sudo ./backup.sh

# Service, memory, swap, disk, health, Tailscale, and Funnel status.
sudo ./status.sh

# Diagnose a failed service.
sudo journalctl -u ksp-ge-flipper -n 200 --no-pager
```

`update.sh` preserves `/var/lib/ksp-ge-flipper` and keeps `server.jar.previous` for rollback. Test reboot recovery after setup:

```bash
sudo reboot
```

After reconnecting, use `sudo ./status.sh`; both `tailscaled` and `ksp-ge-flipper` should be active and Funnel should still be configured.

## Troubleshooting

- **Java 21 or Maven unavailable:** rerun `sudo ./setup-vm.sh`; it installs `openjdk-21-jdk-headless` and Maven.
- **Out of memory or crash loop:** inspect `sudo journalctl -u ksp-ge-flipper -n 200 --no-pager`; the service intentionally caps the heap at 384 MiB and setup enables 2 GB swap.
- **Health check fails:** check `sudo systemctl status ksp-ge-flipper`, verify `/etc/ksp-ge-flipper.env` still has `KSP_BIND_HOST=127.0.0.1`, and do not print or commit the key.
- **Tailscale login/Funnel fails:** run `sudo tailscale status` and `sudo tailscale funnel status`; confirm your tailnet permits Funnel and complete any administrator approval.
- **HTTP 401:** the plugin's `X-KSP-API-Key` must match the protected VM environment file exactly.
- **Disk full:** run `sudo ./backup.sh` to apply backup rotation, inspect `df -h`, and keep journald limits in place.

## Security checks

The service runs as `kspge`; `/etc/ksp-ge-flipper.env` is mode 600; persistence is in `/var/lib/ksp-ge-flipper`; the Java process is loopback-only; and the intended public path is HTTPS through Funnel. Do not expose TCP 8181 or TCP 5432 to the internet.
