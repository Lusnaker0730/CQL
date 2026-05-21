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
| `twcql.com.conf` | nginx server blocks for :80 (301 redirect) and :443 (TLS + proxy to :8888) |

## Deployment

This directory is the source of truth. To update the VM:

```bash
scp docker/nginx-vm/twcql.com.conf root@187.77.155.248:/etc/nginx/sites-available/twcql.com
ssh root@187.77.155.248 'nginx -t && systemctl reload nginx'
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
