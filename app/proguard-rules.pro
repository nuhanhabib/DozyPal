# Activities, services and views referenced from the manifest/layouts are kept automatically,
# and Firebase ships its own rules. Credential Manager loads its Play Services provider by reflection:
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
