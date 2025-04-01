locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
  }

  hostname = var.hostname

  apim_fdr_json_to_xml_service_api = {
    display_name          = "FDR - JSON to XML API REST"
    description           = "FDR - JSON to XML API REST"
    path                  = "fdr-json-to-xml/service"
    subscription_required = true
    service_url           = null
  }

  fdr_internal = {
    project_id   = "fdr_internal"
  }
}
