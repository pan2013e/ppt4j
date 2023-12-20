import logging
import os
import sys
from logging import log, DEBUG, INFO, ERROR, WARNING

_level = DEBUG if os.environ.get('DEBUG') == '1' else INFO
logging.basicConfig(level=_level, format='[%(filename)-18s] %(levelname)5s - %(message)s')

if os.name == 'nt':
    log(ERROR, 'Windows is not supported in this prototype implementation')
    sys.exit(1)

if not os.path.exists('all.iml'):
    log(ERROR, 'Please run this script in project root')
    sys.exit(1)

__all__ = ['vul4j_prepatch', 'vul4j_postpatch', 'JAVA7_HOME', 'JAVA8_HOME',
           'VUL4J_MAX', 'ANDROID_HOME']

vul4j_prepatch = os.environ.get('VUL4J_PREPATCH', '<not set>')
vul4j_postpatch = os.environ.get('VUL4J_POSTPATCH', '<not set>')
JAVA7_HOME = os.environ.get('JAVA7_HOME', '<not set>')
JAVA8_HOME = os.environ.get('JAVA8_HOME', '<not set>')
ANDROID_HOME = os.environ.get('ANDROID_HOME', '<not set>')
VUL4J_MAX = 79

_error = False

for path in [vul4j_prepatch, vul4j_postpatch]:
    if not os.path.exists(path):
        log(WARNING, f'Dataset directory({path}) not found. Please ignore if you are not building the dataset from scratch.')
        break
for path in [JAVA7_HOME, JAVA8_HOME]:
    if not os.path.exists(path):
        log(WARNING, f'JAVA7_HOME/JAVA8_HOME({path}) not found, check your JDK installation. Please ignore if you are not building the dataset from scratch.')
        break
if not os.path.exists(ANDROID_HOME):
    log(WARNING, f'ANDROID_HOME({ANDROID_HOME}) not found, check your Android SDK installation. Please ignore if you are not building the dataset from scratch.')
if os.system('java -version 2> /dev/null') != 0:
    log(ERROR, 'java not found, check your installation or PATH')
    _error = True
if os.system('mvn -v >/dev/null 2>&1') != 0:
    log(ERROR, 'Maven not found, check your installation or PATH')
    _error = True
if os.system('git --version 2>&1 > /dev/null') != 0:
    log(WARNING, 'git not found, check your installation or PATH. Please ignore if you are not building the dataset from scratch.')
if os.system('vul4j -h 2>&1 > /dev/null') != 0:
    log(WARNING, 'vul4j not found, check your installation or python env. Please ignore if you are not building the dataset from scratch.')

if _error:
    print('Proceed with errors? (y/N) ', end='')
    if input().lower() != 'y':
        sys.exit(1)
