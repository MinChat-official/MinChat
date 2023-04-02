# Remote files
This directory stores data which MinChat fetches at runtime.

The following files are present:

## changelog
Contains a list of versions.

Each version begins with the line `#VERSION {major}.{minor}.{patch}`,
e.g. `#VERSION 1.2.3`. Lines not matching this pattern are to be ignored.

Everything until EOF or the next `#VERSION` line is considered to be the description
of this version.

## default-url
Contains the base url of the official MinChat server.

This url is only to be changed upon a migration.

## latest-stable.json
Contains a JSON object of type `io.minchat.common.BuildVersion`,
representing the latest stable version of the MinChat client.
