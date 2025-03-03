import math
import os
import subprocess
import sys
from logging import log, INFO, ERROR

exclude = [62, 63, 67, 72, 73, 74]


def compile_project():
    ret = os.system(f'mvn clean install -DskipTests >/dev/null 2>&1')
    if ret != 0:
        log(ERROR, 'Compile failed')
        sys.exit(1)


def run_test(ptype, pid, debug=False):
    if not debug:
        compile_project()
    grep = ' | grep "Result:"' if debug else ''
    log(INFO, f'Running test {pid} {ptype}')
    ex = subprocess.Popen(f'java -cp lib/*:framework/target/classes/ ppt4j.Main analyze {pid} {ptype} {grep}',
                          shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = ex.communicate()
    print(out, flush=True)


def run_all_tests():
    compile_project()
    for i in range(1, 117):
        if i in exclude:
            continue
        for j in ['prepatch', 'postpatch']:
            run_test(j, i, debug=True)


def print_help_and_exit():
    print('Usage: python -m scripts.test <gt_binary_type> <patch_id>')
    print('     or python -m scripts.test all')
    sys.exit(1)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print_help_and_exit()
    if sys.argv[1] == 'all':
        run_all_tests()
    else:
        if len(sys.argv) < 3:
            print_help_and_exit()
        run_test(sys.argv[1], sys.argv[2])
