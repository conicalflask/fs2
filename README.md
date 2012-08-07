FS2
===

FS2 is a sophisticated and mature file sharing system for LAN parties with a focus on fast accurate searching, easy browsing and fast transfers.

FS2 uses centralised indexing to provide very fast browsing and searching. Usually one of the computers on your LAN will host the indexnode which will enable other clients to browse each other's files.
Data transfer is peer to peer and automatically load balanced between available sources.

FS2 is not designed to be secure or facilitate internet piracy.
It is to allow the easy transfer of files (such steam backups) at lan parties where Windows file sharing and other programs like DC++ simply don't do searching and browsing well enough. 

The goal for FS2 is to be as easy as possible -zero configuration in most cases- and to be the best possible program for LAN party file sharing.
Indeed, FS2 was developed for the FragSoc (University of York LAN gaming society) and has been used for nearly three years now, transferring hundreds of terrabytes in that time.

Getting Started
===============

FS2 requires Java 1.6 or above and will run on Windows, Linux and OS X. Just double click on the fs2client-X.X.X.jar, or run "java -jar fs2client-X.X.X.jar"

**The latest build can always be found at ftp://empty.org.uk/fs2/ and the fs2 client will automatically update to the latest version available.** If autoupdates are not your thing, then you can disable them in the advanced settings.

If there is already an indexnode active on your network then that's it! You're good to go! Your client will automatically join indexnodes on your LAN.
Just go to the files tab to see what other people are sharing. To share files yourself, click 'Shares' in the 'Configure' menu and add directories to be shared.

FS2 will quickly run out of ram if you share a lot of files or queue a lot for download (>100k files). Go to advanced settings and set your allowed heap size to at least 256MiB for decent performance.
Running out of memory is the primary cause of problems with FS2.


Running an indexnode
==================== 

To use FS2 your network will need an indexnode. You can have more than one but it is not recommended.
As your indexnode is responsible for indexing all the files on your network and managing all the clients, you should use a powerful computer.
For a typical small LAN party of 50 or so people, with 40TiB shared over a few million files your indexnode will use at least 4GiB of ram and quite a lot of processing power.
If you have a very small lan party of <10 people or so, an ordinary peer can easily be the indexnode.
Because indexnodes require so much memory they will benefit from 64bit Java on a 64bit operating system.

**The easy way to run an indexnode is to go to the advanced settings and check the "always run an indexnode" box. Make sure you've set the maximum heap size to be big enough!**


Help and Collaboration
======================

If you find bugs, find something confusing or would like a realistic new feature please feel free to open a new issue on github.
The code does not require any external libraries and will work as an eclipse project with no special configuration. To build jar files, run 'ant' in the directory with the build.xml file.

We're definitely looking for documentation and would be open to fresh contributors. If you've fixed bugs or have implemented new features, we'll happily consider pull requests.
