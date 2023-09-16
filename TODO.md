Before MinChat can undergo the first public release, the following features must be implemented:

| Priority | Status                | Description                                                                                                                 |
|----------|-----------------------|-----------------------------------------------------------------------------------------------------------------------------|
| 1        | Done                  | Custom chat input field that can properly display multi-line text and increase it's own size without scrolling the chat     |
| 1        | Partially implemented | Support for bans, mutes; The corresponding UI for admins                                                                    |
| 1        | Not done              | Client-side checks for user account ban/mute                                                                                |
| 2        | Not done              | Proper GUI chat button, a hint for desktop players telling them that there's a shortcut they can use                        |
| 2        | Not done              | Chat mentions and notifications                                                                                             |
| 2        | Not done              | Discord-style replies, possibly ones that mention the recipent                                                              |
| 3        | Not done              | Overlay style for some parts of the chat ui (e.g. the field above the chat box)                                             |
| 3        | Not done              | System messages and channels only specific users/user groups can speak in; rule, news, overview channels                    |
| 4        | Partially implemented | Automatic gateway reconnect when a failure happens; Failure detection (websocket api sould already have a heartbeat system) |
| 5        | Not done              | Direct messages (?)                                                                                                         |
| 6        | Not done              | Map and scheme sharing inside MinChat (with previews) - may require to expand the server.                                   |
| 6        | Not done              | Windowed chat mode (MKUI already has windows)                                                                               |                                                                                                         |