# Third-party notices

Flying Eaves is MIT-licensed (see `LICENSE`). All of its code is original. A few files reproduce published
algorithms or data tables so that the Java generator gives exactly the same results as the Python one; their
origins and licences are listed here.

## Arm optimized-routines (data tables in `UcrtMath.java`)

`src/main/java/com/flyingeaves/gen/py/UcrtMath.java` contains the tables `POW_INVC`, `POW_LOGC`, `POW_LOGCTAIL` and
`POW_EXP_TAB`. They are entry-for-entry the same as `math/pow_log_data.c` (N = 128) and `math/exp_data.c` (N = 256)
of [Arm optimized-routines](https://github.com/ARM-software/optimized-routines), which is licensed under
`MIT OR Apache-2.0 WITH LLVM-exception`. They are used here under the MIT option:

```
MIT License

Copyright (c) Arm Limited and contributors.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Mersenne Twister MT19937 (`PyRandom.java`)

`src/main/java/com/flyingeaves/gen/py/PyRandom.java` implements the MT19937 generator (as CPython's `random` module
uses it), including its `init_genrand` / `init_by_array` seeding:

```
Copyright (C) 1997 - 2002, Makoto Matsumoto and Takuji Nishimura,
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.

  3. The names of its contributors may not be used to endorse or promote
     products derived from this software without specific prior written
     permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## CPython behaviour (`com.flyingeaves.gen.py`)

`PyRandom`, `PySet`, `PyHash` and `Py` are Java re-implementations of the observable behaviour of CPython 3.13
(the derived methods of `random.Random`, the set table layout and iteration order, `hash()` of ints and tuples,
rounding, `repr(float)`, `json.dumps`), written so the Java generator reproduces the Python generator bit for bit.
CPython is distributed under the [PSF License Agreement](https://docs.python.org/3/license.html).

## Gradle wrapper

`gradlew`, `gradlew.bat` and `gradle/wrapper/` are the standard [Gradle](https://gradle.org) wrapper, licensed under
the Apache License 2.0.
