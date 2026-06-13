LogoRenderer
============

LogoRenderer is a standalone Swing application that renders .llf label layout
files to a visual preview, and can emulate a label printer's TCP communication
protocol for local testing and development.

Part of the Commander4j project — https://www.commander4j.com
Help: https://wiki.commander4j.com/index.php/LogoRenderer

Disclaimer
----------

LogoRenderer is an independent open-source tool and is not a Logopak product.
It is not affiliated with, endorsed by, or supported by Logopak. Logopak,
PowerLeap and related names are trademarks of their respective owners and are
used solely to describe interoperability.

This software is intended for local test and development use only. It is not
intended for, and must not be relied upon for, production label printing. Use
is entirely at the user's own discretion and risk.

Licence
-------

Distributed under the GNU General Public License — see
"Commander4j GNU GENERAL PUBLIC LICENSE.txt". The GPL's no-warranty terms
apply to this software in full.

Build and run
-------------

  mvn package
  java -jar c4j_logorenderer.jar [file.llf]

Demo label layouts and data files are in virtual_disk/c0/.
