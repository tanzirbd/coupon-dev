#!/bin/bash
# Copy the shared Dockerfile template to each microservice
SERVICES=("eureka-server" "api-gateway" "user-service" "coupon-service" "validation-service" "notification-service" "analytics-service")
for svc in "${SERVICES[@]}"; do
  cp Dockerfile.template "$svc/Dockerfile"
  echo "Copied Dockerfile to $svc/"
done
echo "Done. Update EXPOSE port in each Dockerfile if needed."
