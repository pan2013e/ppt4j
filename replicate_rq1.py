import math
import os
import subprocess

exclude = [62, 63, 67, 72, 73, 74]
D1_RANGE = [2, 6, 7, 11, 42, 59, 60] + [i for i in range(80, 117)]

D1 = [0, 0, 0, 0]
D2 = [0, 0, 0, 0]

def classify(gt_type, score):
    assert gt_type in ['prepatch', 'postpatch']
    if math.isnan(score):
        score = 0
    assert 0 <= score <= 1
    if gt_type == 'prepatch':
        if score >= 0.6:
            return 1
        else:
            return 2
    elif gt_type == 'postpatch':
        if score >= 0.6:
            return 0
        else:
            return 3

def place(vul_id):
    assert 1 <= vul_id < 117 and vul_id not in exclude
    if vul_id in D1_RANGE:
        return D1
    else:
        return D2

def acc(tp, fp, tn, fn):
    return (tp + tn) / (tp + fp + tn + fn)

def prec(tp, fp, tn, fn):
    return tp / (tp + fp)

def recall(tp, fp, tn, fn):
    return tp / (tp + fn)

def f1(tp, fp, tn, fn):
    p = prec(tp, fp, tn, fn)
    r = recall(tp, fp, tn, fn)
    return 2 * p * r / (p + r)

os.system('mvn clean install -DskipTests >/dev/null 2>&1')

def fire_single_test(idx, gt_type):
    ex = subprocess.Popen(f'java -cp lib/*:framework/target/classes/ ppt4j.Main analyze {idx} {gt_type} | grep "Result:"',
                          shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = ex.communicate()
    score = float(out.splitlines()[0].split()[-1])
    print(f'#{idx} {gt_type} {score}')
    place(idx)[classify(gt_type, score)] += 1

for idx in range(1, 117):
    if idx in exclude:
        continue
    for gt_type in ['prepatch', 'postpatch']:
        fire_single_test(idx, gt_type)

print('-----------   PPT4J   ------------')
print('    ACC     PREC    RECALL  F1    ')
print('D1  {:.4f}  {:.4f}  {:.4f}  {:.4f}'.format(acc(*D1), prec(*D1), recall(*D1), f1(*D1)))
print('D2  {:.4f}  {:.4f}  {:.4f}  {:.4f}'.format(acc(*D2), prec(*D2), recall(*D2), f1(*D2)))

