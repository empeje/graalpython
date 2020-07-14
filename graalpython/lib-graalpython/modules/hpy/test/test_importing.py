# MIT License
# 
# Copyright (c) 2020, Oracle and/or its affiliates.
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

import pytest
from .support import HPyTest

# this function should probably goes somewhere into hpy.universal and/or and
# hpy package and/or an import hook, or whatever. I do not want to think about
# this now.
def import_module_properly(mod):
    raise NotImplementedError("fix me eventually")

# this was moved from support.py, where it did not belong
## class HPyLoader(ExtensionFileLoader):
##     def create_module(self, spec):
##         import hpy.universal
##         return hpy.universal.load_from_spec(spec)


class TestImporting(HPyTest):

    @pytest.mark.xfail
    def test_importing_attributes(self):
        import sys
        modname = 'mytest'
        so_filename = self.compile_module("""
            @INIT
        """, name=modname)
        mod = import_module_properly(so_filename, modname)
        assert mod in sys.modules
        assert mod.__loader__.name == 'mytest'
        assert mod.__spec__.loader is mod.__loader__
        assert mod.__file__
