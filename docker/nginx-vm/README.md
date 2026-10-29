# VM nginx configuration

Source of truth for the nginx config that runs on the production VM (187.77.155.248) and terminates TLS in front of the Docker stack.

## Topology

```
Client --HTTPS--> Cloudflare --HTTPS (Full strict)--> VM nginx:443 --HTTP--> docker-frontend:8888 --HTTP--> docker-backend:8080
```

Cloudflare SSL mode must be **Full (strict)** so CF validates our origin cert. "Flexible" causes a redirect loop (CF fetches over :80, our server 301s to https, CF forwards to itself).

## Files

| File | Purpose |
|------|---------|
| `twcql.com.conf` | nginx server blocks for :80 (301 redirect) and :443 (TLS + proxy to :8888). Both refuse (403) requests whose TCP peer is not Cloudflare / loopback, and :443 answers 404 on `/grafana/` and `/prometheus/` |
| `update-cloudflare-ips.sh` | Generates `/etc/nginx/conf.d/cloudflare-origin.conf` from Cloudflare's published ranges: `set_real_ip_from` + `real_ip_header CF-Connecting-IP` (visitor IP restored) and the `$cf_origin` geo map the site uses to refuse direct-to-origin requests. Lives at `/usr/local/sbin/` on the VM |
| `update-cloudflare-ips.cron` | `/etc/cron.d/update-cloudflare-ips` — runs the generator weekly (Monday 04:17); it only reloads nginx when the ranges changed and `nginx -t` passes |

## Deployment

This directory is the source of truth. To update the VM:

```bash
scp docker/nginx-vm/update-cloudflare-ips.sh root@187.77.155.248:/usr/local/sbin/update-cloudflare-ips.sh
scp docker/nginx-vm/update-cloudflare-ips.cron root@187.77.155.248:/etc/cron.d/update-cloudflare-ips
scp docker/nginx-vm/twcql.com.conf root@187.77.155.248:/etc/nginx/sites-available/twcql.com
ssh root@187.77.155.248 'chmod 755 /usr/local/sbin/update-cloudflare-ips.sh && /usr/local/sbin/update-cloudflare-ips.sh --no-reload && nginx -t && systemctl reload nginx'
```

`twcql.com.conf` references `$cf_origin`, which only exists once `cloudflare-origin.conf` has been generated — run the script before `nginx -t` on a fresh host or the test fails with an unknown variable.

## Cloudflare-only origin and real visitor IPs (PAT-269)

Before 2026-10-23 anyone could reach the site at `https://187.77.155.248/` with `Host: twcql.com` (skipping Cloudflare's WAF, rate limiting and bot checks), and because nginx forwarded the Cloudflare edge address as the client, the backend's per-IP rate limit and `audit_log.ip_address` were keyed on Cloudflare edge IPs (172.69.x / 162.159.x / 104.x) instead of the visitor.

`cloudflare-origin.conf` fixes both for every server block on the host:

- `set_real_ip_from <every Cloudflare range> + 127.0.0.1 + ::1` and `real_ip_header CF-Connecting-IP` — `$remote_addr`, `X-Real-IP` and the address appended to `X-Forwarded-For` become the visitor. The backend's `ClientIpResolver` (rightmost non-private `X-Forwarded-For` entry) then resolves the visitor. Loopback is trusted so the `cloudflared` tunnel (host network) is covered.
- `geo $realip_remote_addr $cf_origin { ... }` — 1 when the *original* TCP peer was Cloudflare or loopback. The site does `if ($cf_origin = 0) { return 403; }`. nginx's `allow` / `deny` cannot be used for this because they evaluate the address *after* the real_ip rewrite (which is the visitor, never a Cloudflare range).

Verification:

```bash
curl -sk -o /dev/null -w '%{http_code}
' -H 'Host: twcql.com' https://187.77.155.248/        # 403 (direct to origin)
curl -s  -o /dev/null -w '%{http_code}
' https://twcql.com/api/status                          # 200 (via Cloudflare)
curl -sk -o /dev/null -w '%{http_code}
' --resolve twcql.com:443:127.0.0.1 https://twcql.com/  # 200 (loopback / tunnel)
tail -n 5 /var/log/nginx/access.log | cut -d' ' -f1                                               # visitor IPs, not 172.69.x
```

## Monitoring access (Grafana / Prometheus)

The `/grafana/` and `/prometheus/` proxies were removed from `frontend/nginx.conf` (the image default) in PAT-269 and from `docker/nginx.conf` (the file `docker-compose.yml` actually bind-mounts into the frontend container, so the one production runs) in BUG-156; they were reachable from the internet with no authentication. This host config answers 404 on both so an older frontend image or compose file cannot re-expose them. The VM's `docker-compose.override.yml` publishes Grafana on `127.0.0.1:3000` and Prometheus on `127.0.0.1:9090`; open them through an SSH tunnel:

```bash
ssh -N -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 root@187.77.155.248
# then http://localhost:3000 (Grafana) and http://localhost:9090 (Prometheus) in a local browser
```

## Cloudflare Origin Certificate

Origin cert + key live at:

- `/etc/ssl/cloudflare/twcql.com.pem` (644, root:root)
- `/etc/ssl/cloudflare/twcql.com.key` (600, root:root)

Issued by Cloudflare Origin CA, 15-year validity (valid through 2041-04-16). This cert is only trusted by Cloudflare — browsers connecting directly to the origin IP would reject it. Renewal is manual: generate a new cert in the Cloudflare dashboard before expiry.

## Verification

```bash
# Origin serves 200 with HSTS
curl -sI -k --resolve twcql.com:443:127.0.0.1 https://twcql.com/ | head

# Plain HTTP redirects to HTTPS
curl -sI http://127.0.0.1/ -H 'Host: twcql.com' | head

# End-to-end via Cloudflare (after Cloudflare SSL mode is Full strict)
curl -sI https://twcql.com/
```
