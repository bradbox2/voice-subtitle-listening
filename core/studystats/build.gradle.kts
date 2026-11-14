plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(projects.core.data.api)
  api(projects.core.datastore)
  api(projects.core.audiobookshelf.api)
  implementation(projects.core.common)
  implementation(projects.core.initializer)
  implementation(projects.core.subtitle)
  implementation(projects.core.logging.api)
}
