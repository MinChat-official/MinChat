# Disclaimer
What's stated below may not be fully implemented yet.
This project is still in development, but this description is written in advance
to cover most features.
By the end of the development, every claim below will be true.

# MinChat
MinChat is a Mindustry mod that adds an innovative global online chat feature.

This mod allows players to communicate with each other in real time
regardless of where they are or to which mindustry server they're connected tp,
enabling them to connect with other Mindustry players outside Discord™ or other generic chat platforms.

We __do not__ and __will not__ connect any confidential
data, other than what's required to authenticate you.

# Rules
1. Do not discriminate anyone regardless of their interests, race, sexuality, gender, etc.
2. Do not insult others (this may be ignored in certain cases if the other side is fine with that). Do not harass anyone.
3. Do not abuse MinChat servers. This includes denial-of-service attacks, message spam, usage of any dangerous exploits, etc.
4. Do not post unwanted advertisements. Nobody cares about your nsfw discord server with 69 people.
5. Do not attempt to scam other users of MinChat.
6. Do not post NSFW or gore content in public chats. Use DMs.
7. Do not send people unwanted content. E.g. do not send NSFW content to strangers unless you know they want it. This applies to DMs.
8. Use common sense. Just because something bad is not listed here does not mean you will not be punished for doing it.

These rules vary in severity. Violation of rules 3 and 5 are likely to be met with a permanent ban.
Violation of rules 1 or 4 will likely result in message deletion and/or temporary mute (up to a few days).

However, if you violate a rule, it's up to admins to decide on the actual punishment.
If you keep violating rules over and over again with no sign of improvement, you will likely be banned.

# Structure
MinChat has the following structure:

* Remote servers (by default a global one is used, however you can host your own and make users connect to it. 
  Only one server can be connected to at a time)
  * Channels - a remote server provides a list of channels you can chat in.
    * Messages - each channel can contain messages, and each registered user can send messages in channels they have access to.
      Each message has an author, a timestamp, a text content, an id, and so on.
  * Users - those are people who have personal accounts in MinChat. Each user must have a unique username-discriminator pair.
    The username and the discriminator cannot be changed once the account is created, however, a user can set a "display name",
    which will be shown by default instead of their username-discriminator pair.

# Tutorials
There are several tutorials scattered across the mod of MinChat that teach you the basics. They are only shown once
and triggered when you do certain things, open certain menus, etc. You can re-take or see any tutorial at any time
by navigating to settings, pressing "minchat", and then pressing "tutorials". There you will see a list of all tutorials
and will be able to reset or immediately view them.

# Registration
In order to register in MinChat, you need to install the mod first. You can do this using Mindustry's built-in mod browser:
navigate to the "mods" menu, press "mod browser", type "Minchat" in the search bar, press on the mod and click "install".

After restarting the game you will see a "Minchat" button in menu. It's the main entrypoint to MinChat.
Pressing this button opens the MinChat dialog. You will see a quick tutorial telling you about some basic things in MinChat.

Once you're done with the tutorial, you can press the button in top right corner which should say something like "not logged in".
This opens your user dialog and triggers another tutorial. After that you can press "register", create a username-password,
and create your first MinChat account. Later you will be able to log in using the same username-password pair. Make sure not to lose it.

# Punishments
If you slightly break the rules (see above), admins can delete your message and perhaps give you a verbal warning.

If you perform a severe violation, you can be muted. This will prevent you from chatting (either at all, or in specific channels).
A very severe violation can result in a ban.

A punishment can have a duration (e.g. a few hours or days), or be permanent.

You are not allowed to use alternate accounts to evade punishments.
(but if you do anyway, do it quietly. Please. Do not make me implement IP bans. I don't want to.)


# Navigating in MinChat
On the left side of the main MinChat dialog you can see a list of channels.
Clicking on one of them will open the channel (see below). You can also right-click (long-press on mobile) a channel
in order to see its stats: the name, the description, the id, etc.

# Channels
Once you open a channel and wait a second for it to load, you will info about it at the top.
There are two labels: the first tells you its name, the second tells you its description.

You can press the top panel in order to see the stats of the channel (as mentioned before).

In the right part of the screen you will see a list of messages in this channel.
A message consists of the following parts:
* The author's name in the top left part - clicking on it allows you to see the author's stats (similarly to channel stats).
* The time of creation in the top right part.
* The content of the message below everything

Similarly to channels, messages can be right-clicked (long-pressed on mobile).
Doing so opens a message context menu, which includes the following actions:
* Copy message - copies the content of the message to your clipboard.
* Edit message - allows you to edit your message. Obviously, this works only with messages sent by you.
* Delete message - allows you to delete your message. Admins can delete messages of others.

# Changing your display name or deleting your account
1. Press on your name in the top left corner to open your stat dialog
2. Press "edit" or "delete" depending on what you want to do.
3. If you chose "edit", enter a new display name and press "confirm". If you chose "delete",
   enter the number shown above and press "confirm".

Important note: deleting your account __does not__ delete your messages, nor any other content left by you in MinChat.
Other users will see "<deleted_user>" instead of your name, but your messages will remain.

# Compiling the client mod

-----

### The following instructions assume you use Linux or UNIX-BASED OS!
#### You can get this to work on windows, but you'll have to figure some things out yourself!

-----

To compile the client mod, navigate to the project directory and run `./gradlew release`.

Before doing that, you must set up an android sdk, an ANDROID_HOME environment variable, and have d8 in your PATH.

----

The first can be done using IntelliJ Idea's android plugin
(press ctrl-shift-A, type "sdk", click on "sdk manager", then install one of the sdks here and press the download button next to it and follow the instructions).

-----

To do the second, edit the file /etc/environment (e.g. via `sudo nano /etc/environment`)
and add the following line, and then reboot your system.

`ANDROID_HOME=/home/fox/Android/Sdk/`

If you're experienced, you can use any different method of declaring this variable, but /etc/environment is the most reliable.
Additionally, if your sdk installation location differs from what's shown above, you must specify that location instead of the above.

-----

For the last part, edit the `/etc/profile` file (e.g. using `sudo nano /etc/profile`) and add the following line at the end, and then restart the system.

```bash
export PATH="$PATH:SDK_PATH_HERE/build-tools/YOUR_VERSION_HERE"
```

Replace "SDK_PATH_HERE" with what you used in step 2
and "YOUR_VERSION_HERE" with the name of a directory you find in $HOME/Android/Sdk/build-tools/.
It should look like this: `33.0.2`.

If you don't feel like editing the systen-wide profile file you may try to edit $HOME/.profile, that is up to you.

-----

After you run the "release" gradle task, you can find a jar file in `minchat-client/build/libs/` named `minchat-client-any-platform.jar`.
This is the mod file which you can install and use/test.

The `minchat-client.jar` is a desktop-only build. If you only need that, you can run `./gradlew minchat-client:jar` instead.
Note that this mod build will not work on mobile devices and as such should not be published.

# Compiling the backend server
To compile the backend server, simply run the command `./gradlew minchat-backend:jar`. 

You will find the executable jar file in `minchat-backend/build/libs` named `minchat-server.jar`.
You can run it and pass `--help` or `launch --help` as arguments to get further instructions.
