# Disclaimer
What's stated below may not be fully implemented yet.
This project is still in development, but this description is written in advance
to cover most features.
By the end of the development, every claim below will be true.

# MinChat
MinChat is a Mindustry mod that adds an innovative global online chat feature.

This mod allows players to communicate with each other in real time
regardless of where they are or to which server they're connected tp,
enabling them to connect with other Mindustry players outside Discord.

We __do not__ and __will not__ connect any confidential or analytical
data, other than what's required to authenticate you.

# Structure
Tne server provides a list of channels users can talk in. Each channel has
a name and a description.

Each registered user can send messages in any channel they have access to.
Once sent, the message is visible to anyone. The author of a message can
edit or delete it.

Before a user can start communicating in MinChat, they need to create an account;
i.e. register in MinChat. This is done by creating a unique username and a password.
Later, the user will be able to log into that account using their credentials.

A registered user can change their nickname or delete their account.

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
