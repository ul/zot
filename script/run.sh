#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."

OS=`uname`
if [[ "$OS" == 'Darwin' ]]; then
    clj -Sdeps "$(<deps-mac.edn)" -J-XstartOnFirstThread -M -m zot.main
else
    clj -Sdeps "$(<deps-win.edn)" -M -m zot.main
fi
