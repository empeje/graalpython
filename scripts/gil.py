# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import argparse
import glob
import os
import re

# detects the beginning of an exported message
PTRN_MESSAGE = re.compile(
    r"@ExportMessage(?P<header>.*?)(?P<method>\s[a-zA-Z][a-zA-Z0-9]*)\((?P<args>.*?)\)(?P<throws>\sthrows .*?)?\s\{",
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_SPECIALIZATION = re.compile(
    r"@(Specialization|Fallback)(?P<header>.*?)(?P<method>\s[a-zA-Z][a-zA-Z0-9]*)\((?P<args>.*?)\)(?P<throws>\sthrows .*?)?\s\{",
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_PACKAGE = re.compile(
    r"package\s.*?;",
    re.DOTALL | re.MULTILINE | re.UNICODE)

RUNTIME_PACKAGE = "package com.oracle.graal.python.runtime;"
GIL_NODE_IMPORT = "import com.oracle.graal.python.runtime.GilNode;"
CACHED_IMPORT = "import com.oracle.truffle.api.dsl.Cached;"
SHARED_IMPORT = "import com.oracle.truffle.api.dsl.Cached.Shared;"


def find_end(match, source, is_class=False):
    end = match.end()
    cnt = 2 if is_class else 1
    i = 0
    for i, chr in enumerate(source[end:]):
        if cnt == 0:
            break
        if chr == '{':
            cnt += 1
        if chr == '}':
            cnt -= 1
    return end + i


class ExportedMessage(object):
    def __init__(self, match, source, start_offset=0, is_class=False, shared=False):
        self.match = match
        self.full_source = source
        self._shared = shared
        self._offset = start_offset
        self._start = match.start()
        self._args_start = match.start('args')
        self._args_end = match.end('args')
        self._throws_start = match.start('throws')
        self._throws_end = match.end('throws')
        self._body_start = match.end()
        self._end = find_end(match, source, is_class=is_class)

    @property
    def start(self):
        return self._offset + self._start

    @property
    def end(self):
        return self._offset + self._end

    @property
    def source(self):
        return self.full_source[self._start: self._end]

    @property
    def header(self):
        return self.full_source[self._start: self._args_start - 1]

    @property
    def args(self):
        return self.full_source[self._args_start: self._args_end]

    @property
    def throws(self):
        return self.full_source[self._throws_start: self._throws_end]

    @property
    def body(self):
        return self.full_source[self._body_start:self._end - 1]

    @property
    def is_fallback(self):
        return '@Fallback' in self.header

    @property
    def is_with_gil(self):
        return "GilNode gil" in self.source

    @property
    def source_with_gil(self):
        # handle varargs ...
        _args = self.args
        if self.is_fallback:
            _uncached_gil = "GilNode gil = GilNode.getUncached();"
        else:
            _uncached_gil = ""
            _args += ", " if self.args else ""
            if self._shared:
                _args += '@Shared("gil")'
            _args += "@Cached GilNode gil"
            if "..." in _args:
                _args = _args.replace("...", "[]")

        return """%s(%s) %s{
    %s boolean mustRelease = gil.acquire();
    try {
        %s
    } finally {
        gil.release(mustRelease);
    }
}""" % (self.header, _args, self.throws, _uncached_gil, self.body.strip())

    def apply_gil(self):
        return

    def __str__(self):
        return "START: {}, ARGS {}:{}, BODY_START: {}, STOP: {}, CONTENT:\n {}".format(
            self._start, self._args_start, self._args_end, self._body_start, self._end, self.source)


def message_is_class(match):
    return ' class ' in match.group('header')


def get_messages(source, pattern, start_offset=0, is_class=False):
    matches = list(re.finditer(pattern, source))
    messages = []
    shared = False
    if (len(matches) > 1 and pattern == PTRN_MESSAGE) or (len(matches) > 2 and pattern == PTRN_SPECIALIZATION):
        shared = True
    for match in matches:
        if message_is_class(match):
            start = match.start()
            end = find_end(match, source, is_class=True)
            messages.extend(get_messages(source[start: end], PTRN_SPECIALIZATION, start_offset=start)[0])
        else:
            messages.append(ExportedMessage(match, source, start_offset=start_offset, is_class=is_class, shared=shared))
    return messages, shared


def add_import(source, shared=False):
    match = list(re.finditer(PTRN_PACKAGE, source))[0]
    end = match.end()
    skip_gil_import = GIL_NODE_IMPORT in source or RUNTIME_PACKAGE in source
    skip_cached_import = CACHED_IMPORT in source
    skip_import_shared = SHARED_IMPORT in source
    gil_import = "" if skip_gil_import else "\n" + GIL_NODE_IMPORT
    cached_import = "" if skip_cached_import else "\n" + CACHED_IMPORT
    if shared:
        shared_import = "" if skip_import_shared else "\n" + SHARED_IMPORT
    else:
        shared_import = ""
    return source[:end] + gil_import + cached_import + shared_import + source[end:]


def file_names_filter(f_name, names):
    names = names.split(",")
    for n in names:
        if n in f_name:
            return True
    return False


def main(sources, add=True, dry_run=True, check_style=True, single_source=False, source_filter=None,
         ignore_filter=None, count=False):
    files = glob.glob("{}**/*.java".format(sources), recursive=True)
    if ignore_filter and not count:
        files = list(filter(lambda f: not file_names_filter(f, ignore_filter), files))
    if source_filter and not count:
        files = list(filter(lambda f: file_names_filter(f, source_filter), files))

    cnt = 0
    for java_file in files:
        with open(java_file, 'r+') as SRC:
            source = SRC.read()
            if add:
                messages, shared = get_messages(source, PTRN_MESSAGE)
                if len(messages) > 0:
                    if count:
                        cnt += 1
                        continue

                    if 'GilNode gil' in source:
                        print("[skipping] {}".format(java_file))
                        continue

                    print("[process] dry run: {}, add: {}. messages: {}, {}".format(
                        dry_run, add, len(messages), java_file))
                    source_with_gil = []
                    m = messages[0]
                    if len(messages) == 1:
                        source_with_gil = [source[:m.start], m.source_with_gil, source[m.end:]]
                    else:
                        source_with_gil.append(source[:m.start])
                        for m1, m2 in zip(messages[:-1], messages[1:]):
                            source_with_gil.append(m1.source_with_gil)
                            source_with_gil.append(source[m1.end: m2.start])
                        source_with_gil.append(m2.source_with_gil)
                        source_with_gil.append(source[m2.end:])

                    source_with_gil = ''.join(source_with_gil)
                    source_with_gil = add_import(source_with_gil, shared=shared)
                    if dry_run:
                        print(source_with_gil)
                        return
                    else:
                        SRC.seek(0)
                        SRC.write(source_with_gil)
                        if single_source:
                            break
            else:
                print("removal of the GIL not yet supported")
                return

    if count:
        print("TO PROCESS: {} files".format(cnt))
    if check_style and not count:
        # running the checkstyle gate (twice)
        for i in range(2):
            os.system("mx python-gate --tags style,python-license")


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry_run", help="do not write any changes, stop after the first file transform",
                        action="store_true")
    parser.add_argument("--count", help="count how many files may need the GIL", action="store_true")
    parser.add_argument("--remove", help="remove the GIL", action="store_true")
    parser.add_argument("--no_style", help="do not run the style checker", action="store_true")
    parser.add_argument("--single", help="stop after modifying the first source", action="store_true")
    parser.add_argument("--filter", type=str, help="filter for source name(s) (comma separated)")
    parser.add_argument("--ignore", type=str, help="ignore filter for source name(s) (comma separated)")
    parser.add_argument("sources", type=str, help="location of sources")
    args = parser.parse_args()

    main(args.sources, add=not args.remove, dry_run=args.dry_run, check_style=not args.no_style,
         single_source=args.single, source_filter=args.filter, ignore_filter=args.ignore, count=args.count)
