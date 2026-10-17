package com.dl.app

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

/** Small algebra engine standing in for math.js: evaluate, symbolic derivative, simplify, math.js-style formatting. */
object Calc {
    private fun rint(v: Double): Double = round(v)

    class CalcError(msg: String) : Exception(msg)

    sealed class N {
        data class Num(val v: Double) : N()
        data class Sym(val name: String) : N()
        data class Bin(val op: Char, val l: N, val r: N) : N()   // + - * / ^
        data class Neg(val x: N) : N()
        data class Fn(val name: String, val args: List<N>) : N()
        data class Fact(val x: N) : N()
    }

    // ───────────── parser ─────────────
    private class Parser(src: String) {
        val s = src.replace("π", "pi").replace("×", "*").replace("÷", "/").replace("−", "-").replace("√", "sqrt").replace(" ", "")
        var i = 0
        fun parse(): N {
            if (s.isEmpty()) throw CalcError("empty")
            val n = expr()
            if (i < s.length) throw CalcError("unexpected ${s[i]}")
            return n
        }
        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun expr(): N {
            var n = term()
            while (peek() == '+' || peek() == '-') { val o = s[i++]; n = N.Bin(o, n, term()) }
            return n
        }
        fun term(): N {
            var n = unary()
            while (true) {
                val c = peek()
                n = when {
                    c == '*' -> { i++; N.Bin('*', n, unary()) }
                    c == '/' -> { i++; N.Bin('/', n, unary()) }
                    c == '(' || c.isLetter() || c.isDigit() || c == '.' -> N.Bin('*', n, power())
                    else -> return n
                }
            }
        }
        fun unary(): N {
            if (peek() == '-') { i++; return N.Neg(unary()) }
            if (peek() == '+') { i++; return unary() }
            return power()
        }
        fun power(): N {
            val b = postfix()
            if (peek() == '^') { i++; return N.Bin('^', b, unary()) }
            return b
        }
        fun postfix(): N {
            var n = atom()
            while (peek() == '!' ) { i++; n = N.Fact(n) }
            return n
        }
        fun atom(): N {
            val c = peek()
            if (c.isDigit() || c == '.') {
                val st = i
                while (peek().isDigit() || peek() == '.') i++
                if ((peek() == 'e' || peek() == 'E') && (s.getOrElse(i + 1) { ' ' }.isDigit() || (s.getOrElse(i + 1) { ' ' } in "+-" && s.getOrElse(i + 2) { ' ' }.isDigit()))) {
                    i++; if (peek() == '+' || peek() == '-') i++
                    while (peek().isDigit()) i++
                }
                return N.Num(s.substring(st, i).toDoubleOrNull() ?: throw CalcError("number"))
            }
            if (c == '(') {
                i++; val n = expr()
                if (peek() == ')') i++ else throw CalcError("missing )")
                return n
            }
            if (c.isLetter()) {
                val st = i
                while (peek().isLetterOrDigit() || peek() == '_') i++
                val name = s.substring(st, i)
                if (peek() == '(') {
                    i++
                    val args = mutableListOf<N>()
                    if (peek() != ')') { args += expr(); while (peek() == ',') { i++; args += expr() } }
                    if (peek() == ')') i++ else throw CalcError("missing )")
                    return N.Fn(name, args)
                }
                return N.Sym(name)
            }
            throw CalcError("unexpected")
        }
    }

    fun parse(src: String): N = Parser(src).parse()

    // ───────────── evaluate ─────────────
    private val consts = mapOf("pi" to PI, "e" to E, "tau" to 2 * PI, "phi" to (1 + sqrt(5.0)) / 2)

    fun eval(n: N, x: Double): Double = when (n) {
        is N.Num -> n.v
        is N.Sym -> if (n.name == "x") x else consts[n.name] ?: throw CalcError("undefined ${n.name}")
        is N.Neg -> -eval(n.x, x)
        is N.Bin -> { val a = eval(n.l, x); val b = eval(n.r, x); when (n.op) { '+' -> a + b; '-' -> a - b; '*' -> a * b; '/' -> a / b; else -> a.pow(b) } }
        is N.Fact -> fact(eval(n.x, x))
        is N.Fn -> { val a = n.args.map { eval(it, x) }; fn(n.name, a) }
    }

    fun eval(src: String, x: Double = 0.0): Double = eval(parse(src), x)

    private fun fact(v: Double): Double {
        if (v < 0 || v != floor(v)) throw CalcError("factorial")
        var r = 1.0; for (k in 2..v.toInt()) r *= k; return r
    }

    private fun fn(name: String, a: List<Double>): Double {
        val v = a.firstOrNull() ?: throw CalcError("args")
        return when (name) {
            "sin" -> sin(v); "cos" -> cos(v); "tan" -> tan(v)
            "asin", "arcsin" -> asin(v); "acos", "arccos" -> acos(v); "atan", "arctan" -> atan(v)
            "atan2" -> atan2(v, a[1]); "sinh" -> sinh(v); "cosh" -> cosh(v); "tanh" -> tanh(v)
            "sqrt" -> sqrt(v); "cbrt" -> Math.cbrt(v); "abs" -> abs(v); "exp" -> exp(v)
            "log" -> if (a.size > 1) ln(v) / ln(a[1]) else ln(v)
            "ln" -> ln(v); "log10" -> log10(v); "log2" -> ln(v) / ln(2.0)
            "floor" -> floor(v); "ceil" -> ceil(v); "round" -> Math.rint(v); "fix" -> truncate(v); "sign" -> sign(v)
            "pow" -> v.pow(a[1]); "mod" -> v - a[1] * floor(v / a[1]); "min" -> a.min(); "max" -> a.max()
            "factorial" -> fact(v)
            else -> throw CalcError("unknown function $name")
        }
    }

    // ───────────── number formatting (math.format, auto notation) ─────────────
    fun fmt(v: Double, precision: Int = 12): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        if (v == 0.0) return "0"
        val a = abs(v)
        val bd = BigDecimal(v).round(MathContext(precision)).stripTrailingZeros()
        if (a < 1e-3 || a >= 1e5) {
            val unscaled = bd.unscaledValue().abs().toString()
            val exp = bd.precision() - bd.scale() - 1
            val coef = if (unscaled.length > 1) unscaled[0] + "." + unscaled.substring(1) else unscaled
            return (if (v < 0) "-" else "") + coef + "e" + (if (exp >= 0) "+" else "") + exp
        }
        return bd.toPlainString()
    }

    /** JS-style number to string for bounds shown in history. */
    fun js(v: Double): String = if (v == rint(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    // ───────────── derivative ─────────────
    private fun num(v: Double) = N.Num(v)
    private fun has(n: N): Boolean = when (n) {
        is N.Num -> false; is N.Sym -> n.name == "x"; is N.Neg -> has(n.x); is N.Fact -> has(n.x)
        is N.Bin -> has(n.l) || has(n.r); is N.Fn -> n.args.any { has(it) }
    }

    fun hasX(src: String): Boolean = has(parse(src))

    fun d(n: N): N = when (n) {
        is N.Num -> num(0.0)
        is N.Sym -> num(if (n.name == "x") 1.0 else 0.0)
        is N.Neg -> N.Neg(d(n.x))
        is N.Fact -> throw CalcError("derivative of factorial")
        is N.Bin -> when (n.op) {
            '+', '-' -> N.Bin(n.op, d(n.l), d(n.r))
            '*' -> N.Bin('+', N.Bin('*', d(n.l), n.r), N.Bin('*', n.l, d(n.r)))
            '/' -> N.Bin('/', N.Bin('-', N.Bin('*', d(n.l), n.r), N.Bin('*', n.l, d(n.r))), N.Bin('^', n.r, num(2.0)))
            else -> when {
                !has(n.r) -> N.Bin('*', N.Bin('*', n.r, N.Bin('^', n.l, N.Bin('-', n.r, num(1.0)))), d(n.l))
                !has(n.l) -> N.Bin('*', N.Bin('*', n, N.Fn("log", listOf(n.l))), d(n.r))
                else -> N.Bin('*', n, N.Bin('+', N.Bin('*', d(n.r), N.Fn("log", listOf(n.l))), N.Bin('/', N.Bin('*', n.r, d(n.l)), n.l)))
            }
        }
        is N.Fn -> {
            val u = n.args[0]; val du = d(u)
            val outer: N = when (n.name) {
                "sin" -> N.Fn("cos", listOf(u)); "cos" -> N.Neg(N.Fn("sin", listOf(u)))
                "tan" -> N.Bin('/', num(1.0), N.Bin('^', N.Fn("cos", listOf(u)), num(2.0)))
                "asin" -> N.Bin('/', num(1.0), N.Fn("sqrt", listOf(N.Bin('-', num(1.0), N.Bin('^', u, num(2.0))))))
                "acos" -> N.Neg(N.Bin('/', num(1.0), N.Fn("sqrt", listOf(N.Bin('-', num(1.0), N.Bin('^', u, num(2.0)))))))
                "atan" -> N.Bin('/', num(1.0), N.Bin('+', num(1.0), N.Bin('^', u, num(2.0))))
                "sinh" -> N.Fn("cosh", listOf(u)); "cosh" -> N.Fn("sinh", listOf(u))
                "tanh" -> N.Bin('/', num(1.0), N.Bin('^', N.Fn("cosh", listOf(u)), num(2.0)))
                "sqrt" -> N.Bin('/', num(1.0), N.Bin('*', num(2.0), n))
                "exp" -> n
                "log", "ln" -> N.Bin('/', num(1.0), u)
                "log10" -> N.Bin('/', num(1.0), N.Bin('*', u, N.Fn("log", listOf(num(10.0)))))
                "abs" -> N.Fn("sign", listOf(u))
                else -> throw CalcError("derivative of ${n.name}")
            }
            N.Bin('*', outer, du)
        }
    }

    // ───────────── simplify (polynomial-style normal form) ─────────────
    // term key: list of (baseText, exponent) sorted; value: coefficient
    private class Term(val f: List<Pair<String, Double>>, val c: Double)

    private fun merge(ts: List<Term>): List<Term> {
        val m = LinkedHashMap<String, Term>()
        ts.forEach { t ->
            val k = t.f.joinToString("|") { it.first + "^" + it.second }
            val ex = m[k]
            m[k] = if (ex == null) t else Term(t.f, ex.c + t.c)
        }
        return m.values.filter { abs(it.c) > 1e-12 }
    }

    private fun mulTerms(a: Term, b: Term): Term {
        val f = LinkedHashMap<String, Double>()
        (a.f + b.f).forEach { f[it.first] = (f[it.first] ?: 0.0) + it.second }
        return Term(f.filter { abs(it.value) > 1e-12 }.toList().sortedBy { it.first }, a.c * b.c)
    }


    private fun poly(n: N): List<Term> = when (n) {
        is N.Num -> if (n.v == 0.0) emptyList() else listOf(Term(emptyList(), n.v))
        is N.Sym -> listOf(Term(listOf(n.name to 1.0), 1.0))
        is N.Neg -> poly(n.x).map { Term(it.f, -it.c) }
        is N.Fact -> { val p = poly(n.x); if (p.size == 1 && p[0].f.isEmpty() && p[0].c >= 0 && p[0].c == floor(p[0].c)) poly(num(fact(p[0].c))) else listOf(Term(listOf(print(p) + "!" to 1.0), 1.0)) }
        is N.Bin -> when (n.op) {
            '+' -> merge(poly(n.l) + poly(n.r))
            '-' -> merge(poly(n.l) + poly(n.r).map { Term(it.f, -it.c) })
            '*' -> {
                val a = poly(n.l); val b = poly(n.r)
                if (a.isEmpty() || b.isEmpty()) emptyList()
                else if (a.size == 1 || b.size == 1 || isConst(a) || isConst(b)) merge(a.flatMap { x -> b.map { y -> mulTerms(x, y) } })
                else listOf(mulTerms(Term(listOf(wrap(a) to 1.0), 1.0), Term(listOf(wrap(b) to 1.0), 1.0)))
            }
            '/' -> {
                val a = poly(n.l); val b = poly(n.r)
                if (a.isEmpty()) emptyList()
                else if (b.size == 1) merge(a.map { x -> mulTerms(x, Term(b[0].f.map { it.first to -it.second }, 1.0 / b[0].c)) })
                else merge(a.map { x -> mulTerms(x, Term(listOf(wrap(b) to -1.0), 1.0)) })
            }
            else -> {
                val a = poly(n.l); val e = poly(n.r)
                if (e.isEmpty()) listOf(Term(emptyList(), 1.0))
                else if (e.size == 1 && e[0].f.isEmpty()) {
                    val k = e[0].c
                    if (a.isEmpty()) emptyList()
                    else if (a.size == 1 && (k == floor(k) || a[0].c == 1.0)) {
                        val t = a[0]
                        listOf(Term(t.f.map { it.first to it.second * k }, t.c.pow(k)))
                    } else if (k == 1.0) a
                    else listOf(Term(listOf(wrap(a) to k), 1.0))
                } else listOf(Term(listOf("(" + print(a) + ") ^ (" + print(e) + ")" to 1.0), 1.0))
            }
        }
        is N.Fn -> {
            val ps = n.args.map { poly(it) }
            if (ps.isNotEmpty() && ps.all { it.isEmpty() || isConst(it) }) {
                val v = runCatching { fn(n.name, ps.map { if (it.isEmpty()) 0.0 else it[0].c }) }.getOrNull()
                if (v != null && v.isFinite() && abs(v - rint(v)) < 1e-12) poly(num(rint(v)))
                else listOf(Term(listOf(n.name + "(" + ps.joinToString(", ") { print(it) } + ")" to 1.0), 1.0))
            } else listOf(Term(listOf(n.name + "(" + ps.joinToString(", ") { print(it) } + ")" to 1.0), 1.0))
        }
    }

    private fun isConst(p: List<Term>) = p.size == 1 && p[0].f.isEmpty()
    private fun wrap(p: List<Term>): String = "(" + print(p) + ")"
    private fun fnum(v: Double) = fmt(v, 15)
    private fun factorText(b: String, e: Double): String = if (e == 1.0) b else b + " ^ " + fnum(e)

    private fun termText(t: Term, absolute: Boolean): String {
        var c = if (absolute) abs(t.c) else t.c
        if (t.f.isEmpty()) return fnum(c)
        val numer = t.f.filter { it.second > 0 }; val denom = t.f.filter { it.second < 0 }
        fun fs(l: List<Pair<String, Double>>) = l.joinToString(" * ") { factorText(it.first, abs(it.second)) }
        var div: String? = null
        if (denom.isEmpty() && abs(c) < 1 && abs(c) > 0) {
            val inv = 1 / abs(c)
            if (abs(inv - rint(inv)) < 1e-9) { div = fnum(rint(inv)); c = if (c < 0) -1.0 else 1.0 }
        }
        val mag = abs(c)
        var s = when {
            numer.isEmpty() -> if (mag == 1.0) "1" else fnum(mag)
            mag == 1.0 -> fs(numer)
            else -> fnum(mag) + " * " + fs(numer)
        }
        if (denom.isNotEmpty()) s += " / " + (if (denom.size > 1) "(" + fs(denom) + ")" else fs(denom))
        if (div != null) s += " / $div"
        return (if (c < 0) "-" else "") + s
    }

    private fun print(ts: List<Term>): String {
        if (ts.isEmpty()) return "0"
        val sorted = ts.sortedWith(compareBy<Term>({ -it.f.filter { f -> f.second > 0 }.sumOf { f -> f.second } }, { it.f.joinToString { f -> f.first } }))
        val sb = StringBuilder()
        sorted.forEachIndexed { i, t ->
            if (i == 0) sb.append(termText(t, false))
            else sb.append(if (t.c < 0) " - " else " + ").append(termText(t, true))
        }
        return sb.toString()
    }

    fun simplify(src: String): String = print(poly(parse(src)))

    fun derivative(src: String): String = print(poly(d(parse(src))))
    fun derivativeNode(src: String): N = d(parse(src))

    // ───────────── solve ─────────────
    fun solve(raw: String): String {
        val sides = raw.split("=")
        val left = sides[0]; val right = if (sides.size > 1) sides[1] else "0"
        val expr = "($left)-($right)"
        val n = parse(expr)
        val c = eval(n, 0.0)
        val d1 = d(n); val b = eval(d1, 0.0)
        val a = eval(d(d1), 0.0) / 2
        val probe = eval(n, 5.0) - (c + b * 5 + a * 25)
        if (abs(probe) > 1e-6 || probe.isNaN()) throw CalcError("degree too high")
        if (abs(a) < 1e-9) {
            if (abs(b) < 1e-9) return if (abs(c) < 1e-9) "TRUE FOR EVERY x" else "NO SOLUTION"
            return "x = " + fmt(-c / b, 10)
        }
        val disc = b * b - 4 * a * c
        if (disc < 0) return "x = " + fmt(-b / (2 * a), 8) + " ± " + fmt(sqrt(-disc) / (2 * a), 15) + "i"
        val sq = sqrt(disc); val x1 = (-b + sq) / (2 * a); val x2 = (-b - sq) / (2 * a)
        if (abs(x1 - x2) < 1e-9) return "x = " + fmt(x1, 10)
        return "x = " + fmt(x1, 8) + "  or  x = " + fmt(x2, 8)
    }

    fun integral(src: String, a: Double, b: Double): Double {
        val n = parse(src); var m = 1200; if (m % 2 == 1) m++
        val h = (b - a) / m; var sum = 0.0
        for (i in 0..m) {
            val x = a + i * h; val y = eval(n, x)
            if (!y.isFinite()) throw CalcError("bad function")
            sum += if (i == 0 || i == m) y else if (i % 2 == 0) 2 * y else 4 * y
        }
        return h * sum / 3
    }
}
