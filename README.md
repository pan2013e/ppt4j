# PPT4J

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.10397354.svg)](https://doi.org/10.5281/zenodo.10397354)
[![Docker](https://img.shields.io/badge/Docker%20Hub-zhiyuanpan/ppt4j-blue.svg)](https://hub.docker.com/r/zhiyuanpan/ppt4j)


This is the documentation page for the replication package of the ICSE 2024 paper *"PPT4J: Patch Presence Test for Java Binaries"*.

**Note:** For more details on the paper, please refer to [https://dl.acm.org/doi/abs/10.1145/3597503.3639231](https://dl.acm.org/doi/abs/10.1145/3597503.3639231).

**Note:** This website is produced using **AI-generated** code comments and docstrings.


---

## Project structure

```
.
├── Dockerfile
├── README.md
├── all.iml
├── download.sh
├── framework
├── lib
├── misc
├── pom.xml
├── replicate_rq1.py
├── scripts
└── urls.txt
```

- The `framework` folder contains the code of PPT4J
- The `misc` folder contains an example of using PPT4J
  - Please checkout `misc/src/main/java/ppt4j/Demo.java` for detailed usage.
- The `scripts` folder contains utility scripts, written in Python
- The `lib` folder contains necessary jars

## Requirements

Note: We provide Docker images for replication. If you use Docker, you can just jump to the section "Using docker".

### System requirements

- This project is a prototype implementation and only supports UNIX-like operating systems.

### Software requirements

- **JDK, Maven, Python**
- The versions of software we use:
  - JDK 17.0.2 (Java SE 17.0.2, Oracle HotSpot 64-Bit Server VM, build 17.0.2+8-LTS-86)
  - Apache Maven 3.9.1
  - Python 3.9.10
- It should be fine if you use other recent versions of Maven and Python.
- We strongly recommend that you use the same Oracle JDK 17.0.2 for replication. Run `export JAVA_HOME=...` command to make sure that maven uses the expected version of JDK.

## Setting up the environment

### On host machine

- [Download the dataset](https://doi.org/10.5281/zenodo.10397354) and extract the items to your preferred location. Then set 
  - `ppt4j.database.root` field in `framework/src/main/resources/ppt4j.properties` to that location. Or you can just extract to your home directory to make the property file unaltered.

### Using Docker

- We provide a [Docker image](https://hub.docker.com/r/zhiyuanpan/ppt4j) as an alternative. To start a container, the host system and architecture should be `Linux, x86_64(amd64)`.

```bash
# Host OS
$ docker pull zhiyuanpan/ppt4j
$ # alternative: docker pull zhiyuanpan/ppt4j:minimal-latest
$ docker run --rm -it zhiyuanpan/ppt4j
```

- The docker images on Docker Hub have two tags: `minimal`(~250M download size, ~500M final usage) and `complete`(~13G download size, ~22G final usage). 
  - The `complete` one provides an out-of-the-box experience (with dataset). Commands described in the next section can be executed without any further configuration.
  - The `minimal` one provides a minimal running environment that satisfies software requirements (without dataset). To test the dataset in `minimal` containers, please first run `bash download.sh` in the **home directory**, and the script will start the downloading process. 
- To test other Java binaries, you can mount custom directories in the `docker run` command.

For detailed script/executable usage, please refer to the documentation below.

## Run PPT4J

For a quick start, you can run `python replicate_rq1.py` to get the results of RQ1 in the paper.

You can also use the Python scripts inside the `scripts` folder for: **project building** and **testing**.

- In the **project root** (where `README.md` and `all.iml` are located), run `python -m scripts.<name> <args>`.
  - Available `<name>`: `build, test`
  - `scripts.build`: wrapper of maven commands; no arg; build the `Java` projects
  - `scripts.test`: perform tests on the dataset. Please leave `<args>` empty for detailed usage of this subcommand.
- Example
```bash
$ python -m scripts.build
$ python -m scripts.test postpatch 1

[test.py           ]  INFO - Running test 1 postpatch
[.a.p.PatchAnalyzer]  INFO - Ground truth binary type in dataset: postpatch
[.a.p.PatchAnalyzer]  INFO - Analyzing patch for fastjson, CVE ID: CVE-2017-18349, Database ID: 1
[.a.p.PatchAnalyzer]  INFO - Result: 1.0
[p.u.AspectUtils   ]  INFO - Result: patch is present
```

```bash
$ python -m scripts.test all
```

## Misc
- The compiled PPT4J only works as separate bytecode files in `framework/target/classes`. Packing them into a jar and use the jar will lead to unexpected behaviours, due to our implementation and the classpath mechanism in newer Java versions.
