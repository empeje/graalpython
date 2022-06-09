# MIT License
# 
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import sys
import os.path
import functools
import re
from pathlib import Path
from distutils import log
from distutils.ccompiler import new_compiler
from distutils.command.build import build
from distutils.errors import DistutilsError
from distutils import sysconfig
from setuptools.command import bdist_egg as bdist_egg_mod
from setuptools.command.build_ext import build_ext

# NOTE: this file is also imported by PyPy tests, so it must be compatible
# with both Python 2.7 and Python 3.x

DEFAULT_HPY_ABI = 'universal'
if hasattr(sys, 'implementation') and sys.implementation.name == 'cpython':
    DEFAULT_HPY_ABI = 'cpython'

class HPyDevel:
    """ Extra sources for building HPy extensions with hpy.devel. """

    _DEFAULT_BASE_DIR = Path(__file__).parent

    def __init__(self, base_dir=_DEFAULT_BASE_DIR):
        self.base_dir = Path(base_dir)
        self.include_dir = self.base_dir.joinpath('include')
        self.src_dir = self.base_dir.joinpath('src', 'runtime')
        self._ctx_lib = None

    def get_extra_include_dirs(self):
        """ Extra include directories needed by extensions in both CPython and
            Universal modes.
        """
        return list(map(str, [
            self.include_dir,
        ]))

    def get_extra_sources(self):
        """ Extra sources needed by extensions in both CPython and Universal
            modes.
        """
        return list(map(str, [
            self.src_dir.joinpath('argparse.c'),
            self.src_dir.joinpath('buildvalue.c'),
            self.src_dir.joinpath('helpers.c'),
            self.src_dir.joinpath('structseq.c'),
        ]))

    def get_ctx_sources(self):
        """ Extra sources needed only in the CPython ABI mode.
        """
        return list(map(str, self.src_dir.glob('ctx_*.c')))

    def get_ctx_lib(self):
        ctx_lib = self._ctx_lib
        if not ctx_lib:
            workdir = os.path.join(sysconfig.get_python_lib(plat_specific=1, standard_lib=1), "hpy.devel")
            lib_name = "cpy_abi"
            sources = self.get_ctx_sources()
            ctx_lib = HPyDevel._compile_ctx_sources(sources, lib_name, workdir)
            self._ctx_lib = ctx_lib
        return ctx_lib

    @staticmethod
    def _compile_ctx_sources(sources, lib_name, output_dir):
        """ Compiles the sources 'get_ctx_sources' and creates a static library.
        """
        c = new_compiler()
        # the compiler needs to be "customized" such that it uses the right flags etc.
        sysconfig.customize_compiler(c)
        ctx_lib = c.library_filename(lib_name, output_dir=output_dir)
        if HPyDevel._ctx_lib_needsBuild(sources, ctx_lib):
            # Create compiler with default options
            py_include = sysconfig.get_python_inc()
            include_dirs = [py_include]
            plat_py_include = sysconfig.get_python_inc(plat_specific=1)
            if plat_py_include != py_include:
                include_dirs.append(plat_py_include)
            objects = c.compile(sources, output_dir=output_dir, include_dirs=include_dirs)
            c.create_static_lib(objects, lib_name, output_dir=output_dir)
        return ctx_lib

    @staticmethod
    def _ctx_lib_needsBuild(sources, ctx_lib):
        return True
        if not os.path.exists(ctx_lib):
            log.debug("Building %s (did not exist)", ctx_lib)
            return True
        # Compare timestamps; if any sources is newer than 'ctx_lib' -> rebuild
        ts_newest_source = 0
        newest_source_file = None
        for f in sources:
            ts = os.path.getmtime(f)
            if ts_newest_source < ts:
                ts_newest_source = ts
                newest_source_file = f
        ts_lib = os.path.getmtime(ctx_lib)
        if ts_lib < ts_newest_source:
            log.debug("Rebuild needed for %s (%s newer than %s)", ctx_lib, newest_source_file, ts_lib)
            return True
        log.debug("%s is up-to-date", ctx_lib)
        return False

    def fix_distribution(self, dist):
        """ Override build_ext to support hpy modules.

            Used from both setup.py and hpy/test.
        """
        dist.hpydevel = self

        base_build = dist.cmdclass.get("build", build)
        base_build_ext = dist.cmdclass.get("build_ext", build_ext)
        orig_bdist_egg_write_stub = bdist_egg_mod.write_stub

        if isinstance(base_build_ext, type):
            assert ('setuptools.command.build_ext', 'build_ext') in [
                (c.__module__, c.__name__) for c in base_build_ext.__mro__
            ], (
                "dist.cmdclass['build_ext'] does not inherit from"
                " setuptools.command.build_ext.build_ext. The HPy build"
                " system does not currently support any other build_ext"
                " classes. If you are using distutils.commands.build_ext,"
                " please use setuptools.commands.build_ext instead."
            )

        class build_hpy_ext(build_hpy_ext_mixin, base_build_ext, object):
            _base_build_ext = base_build_ext

        def dist_has_ext_modules(self):
            if self.ext_modules or self.hpy_ext_modules:
                return True
            return False

        def build_has_ext_modules(self):
            return self.distribution.has_ext_modules()

        def bdist_egg_write_stub(resource, pyfile):
            if resource.endswith(".hpy.so"):
                log.info("stub file already created for %s", resource)
                return
            orig_bdist_egg_write_stub(resource, pyfile)

        # replace build_ext subcommand
        dist.cmdclass['build_ext'] = build_hpy_ext
        dist.__class__.has_ext_modules = dist_has_ext_modules
        base_build.has_ext_modules = build_has_ext_modules
        # setuptools / distutils store subcommands in .subcommands which
        # is a list of tuples of (extension_name, extension_needs_to_run_func).
        # The two lines below replace .subcommand entry for build_ext.
        idx = [sub[0] for sub in base_build.sub_commands].index("build_ext")
        base_build.sub_commands[idx] = ("build_ext", build_has_ext_modules)
        bdist_egg_mod.write_stub = bdist_egg_write_stub


def handle_hpy_ext_modules(dist, attr, hpy_ext_modules):
    """ Distuils hpy_ext_module setup(...) argument and --hpy-abi option.

        See hpy's setup.py where this function is registered as an entry
        point.
    """
    assert attr == 'hpy_ext_modules'

    # add a global option --hpy-abi to setup.py
    dist.__class__.hpy_abi = DEFAULT_HPY_ABI
    dist.__class__.global_options += [
        ('hpy-abi=', None, 'Specify the HPy ABI mode (default: %s)' % DEFAULT_HPY_ABI)
    ]
    hpydevel = HPyDevel()
    hpydevel.fix_distribution(dist)


_HPY_UNIVERSAL_MODULE_STUB_TEMPLATE = """
# DO NOT EDIT THIS FILE!
# This file is automatically generated by hpy

def __bootstrap__():

    from sys import modules
    from os import environ
    from pkg_resources import resource_filename
    from hpy.universal import load
    env_debug = environ.get('HPY_DEBUG')
    is_debug = env_debug is not None and (env_debug == "1" or __name__ in env_debug.split(","))
    ext_filepath = resource_filename(__name__, {ext_file!r})
    if 'HPY_LOG' in environ:
        if is_debug:
            print("Loading {module_name!r} in HPy universal mode with a debug context")
        else:
            print("Loading {module_name!r} in HPy universal mode")
    m = load({module_name!r}, ext_filepath, debug=is_debug)
    m.__file__ = ext_filepath
    m.__loader__ = __loader__
    m.__name__ = __name__
    m.__package__ = __package__
    m.__spec__ = __spec__
    m.__spec__.origin = ext_filepath
    modules[__name__] = m

__bootstrap__()
"""


class HPyExtensionName(str):
    """ Wrapper around str to allow HPy extension modules to be identified.

        The following build_ext command methods are passed only the *name*
        of the extension and not the full extension object. The
        build_hpy_ext_mixin class needs to detect when HPy are extensions
        passed to these methods and override the default behaviour.

        This str sub-class allows HPy extensions to be detected, while
        still allowing the extension name to be used as an ordinary string.
    """

    def split(self, *args, **kw):
        result = str.split(self, *args, **kw)
        return [self.__class__(s) for s in result]

    def translate(self, *args, **kw):
        result = str.translate(self, *args, **kw)
        return self.__class__(result)


def is_hpy_extension(ext_name):
    """ Return True if the extension name is for an HPy extension. """
    return isinstance(ext_name, HPyExtensionName)


def remember_hpy_extension(f):
    """ Decorator for remembering whether an extension name belongs to an
        HPy extension.
    """
    @functools.wraps(f)
    def wrapper(self, ext_name):
        if self._only_hpy_extensions:
            assert is_hpy_extension(ext_name), (
                "Extension name %r is not marked as an HPyExtensionName"
                " but only HPy extensions are present. This is almost"
                " certainly a bug in HPy's overriding of setuptools"
                " build_ext. Please report this error the HPy maintainers."
                % (ext_name,)
            )
        result = f(self, ext_name)
        if is_hpy_extension(ext_name):
            result = HPyExtensionName(result)
        return result
    return wrapper


class build_hpy_ext_mixin:
    """ A mixin class for setuptools build_ext to add support for buidling
        HPy extensions.
    """

    # Ideally we would have simply added the HPy extensions to .extensions
    # at the end of .initialize_options() but the setuptools build_ext
    # .finalize_options both iterate over and needless overwrite the
    # .extensions attribute, so we hide the full extension list in
    # ._extensions and expose it as a settable property that ignores attempts
    # to overwrite it:

    _extensions = None

    @property
    def extensions(self):
        return self._extensions

    @extensions.setter
    def extensions(self, value):
        pass  # ignore any attempts to change the list of extensions directly

    def initialize_options(self):
        self._base_build_ext.initialize_options(self)
        self.hpydevel = self.distribution.hpydevel

    def _finalize_hpy_ext(self, ext):
        if hasattr(ext, "hpy_abi"):
            return
        ext.name = HPyExtensionName(ext.name)
        ext.hpy_abi = self.distribution.hpy_abi
        ext.include_dirs += self.hpydevel.get_extra_include_dirs()
        ext.sources += self.hpydevel.get_extra_sources()
        ext.define_macros.append(('HPY', None))
        if ext.hpy_abi == 'cpython':
            # ext.sources += self.hpydevel.get_ctx_sources()
            ext.extra_objects.append(self.hpydevel.get_ctx_lib())
            ext._hpy_needs_stub = False
        elif ext.hpy_abi == 'universal':
            ext.define_macros.append(('HPY_UNIVERSAL_ABI', None))
            ext._hpy_needs_stub = True
        else:
            raise DistutilsError('Unknown HPy ABI: %s. Valid values are: '
                                 'cpython, universal' % ext.hpy_abi)

    def finalize_options(self):
        self._extensions = self.distribution.ext_modules or []
        # _only_hpy_extensions is used only as a sanity check that no
        # hpy extensions are misidentified as legacy C API extensions in the
        # case where only hpy extensions are present.
        self._only_hpy_extensions = not bool(self._extensions)
        hpy_ext_modules = self.distribution.hpy_ext_modules or []
        for ext in hpy_ext_modules:
            self._finalize_hpy_ext(ext)
        self._extensions.extend(hpy_ext_modules)
        self._base_build_ext.finalize_options(self)
        for ext in hpy_ext_modules:
            ext._needs_stub = ext._hpy_needs_stub

    @remember_hpy_extension
    def get_ext_fullname(self, ext_name):
        return self._base_build_ext.get_ext_fullname(self, ext_name)

    @remember_hpy_extension
    def get_ext_fullpath(self, ext_name):
        return self._base_build_ext.get_ext_fullpath(self, ext_name)

    @remember_hpy_extension
    def get_ext_filename(self, ext_name):
        if not is_hpy_extension(ext_name):
            return self._base_build_ext.get_ext_filename(self, ext_name)
        if self.distribution.hpy_abi == 'universal':
            ext_path = ext_name.split('.')
            ext_suffix = '.hpy.so'  # XXX Windows?
            ext_filename = os.path.join(*ext_path) + ext_suffix
        else:
            ext_filename = self._base_build_ext.get_ext_filename(
                self, ext_name)
        return ext_filename

    def write_stub(self, output_dir, ext, compile=False):
        if (not hasattr(ext, "hpy_abi") or
                self.distribution.hpy_abi != 'universal'):
            return self._base_build_ext.write_stub(
                self, output_dir, ext, compile=compile)
        pkgs = ext._full_name.split('.')
        if compile:
            # compile is true when .write_stub is called while copying
            # extensions to the source folder as part of build_ext --inplace.
            # In this situation, output_dir includes the folders that make up
            # the packages containing the module. When compile is false,
            # output_dir does not include those folders (and is just the
            # build_lib folder).
            pkgs = [pkgs[-1]]
        stub_file = os.path.join(output_dir, *pkgs) + '.py'
        log.info(
            "writing hpy universal stub loader for %s to %s",
            ext._full_name, stub_file)

        ext_file = os.path.basename(ext._file_name)
        module_name = ext_file.split(".")[0]
        if not self.dry_run:
            with open(stub_file, 'w') as f:
                f.write(_HPY_UNIVERSAL_MODULE_STUB_TEMPLATE.format(
                    ext_file=ext_file, module_name=module_name)
                )

    def get_export_symbols(self, ext):
        """ Override .get_export_symbols to replace "PyInit_<module_name>"
            with "HPyInit_<module_name>.

            Only relevant on Windows, where the .pyd file (DLL) must export the
            module "HPyInit_" function.
        """
        exports = self._base_build_ext.get_export_symbols(self, ext)
        if hasattr(ext, "hpy_abi") and ext.hpy_abi == 'universal':
            exports = [re.sub(r"^PyInit_", "HPyInit_", name) for name in exports]
        return exports
