import logging
import os
import sys
from logging import log, DEBUG, INFO, ERROR

_level = DEBUG if os.environ.get('DEBUG') == '1' else INFO
logging.basicConfig(level=_level, format='[%(filename)-18s] %(levelname)5s - %(message)s')

if os.name == 'nt':
    log(ERROR, 'Windows is not supported in this prototype implementation')
    sys.exit(1)

if not os.path.exists('all.iml'):
    log(ERROR, 'Please run this script in project root')
    sys.exit(1)

_error = False

if os.system('java -version 2> /dev/null') != 0:
    log(ERROR, 'java not found, check your installation or PATH')
    _error = True
if os.system('mvn -v >/dev/null 2>&1') != 0:
    log(ERROR, 'Maven not found, check your installation or PATH')
    _error = True

if _error:
    sys.exit(1)
