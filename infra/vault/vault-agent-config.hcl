auto_auth {
  method "kubernetes" {
    mount_path = "auth/kubernetes"
    config = {
      role = "uip-backend"
    }
  }

  sink "file" {
    config = {
      path = "/vault/.vault-token"
    }
  }
}

template {
  source      = "/vault/templates/database.tpl"
  destination = "/vault/secrets/database.properties"
}

template {
  source      = "/vault/templates/jwt.tpl"
  destination = "/vault/secrets/jwt.properties"
}

template {
  source      = "/vault/templates/kafka.tpl"
  destination = "/vault/secrets/kafka.properties"
}
