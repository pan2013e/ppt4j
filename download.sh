#!/bin/bash

mkdir -p tmp_download
echo "Downloading files from Zenodo"
wget -P tmp_download/ -c -i urls.txt

cd tmp_download
awk '{print $2 "  " $1}' MD5.txt > MD5.chk # fix the file format in zenodo distribution
echo "Merging splitted files"
cat ppt4j_data.tar.xz.part* > ppt4j_data.tar.xz
md5sum --ignore-missing --check MD5.chk

mkdir -p ${HOME}/database
echo "Extracting ppt4j_data.tar.xz to ${HOME}"
tar xf ppt4j_data.tar.xz -C ${HOME}

echo "Cleaning up"
cd ..
rm -rf tmp_download

echo "Dataset downloaded successfully"
