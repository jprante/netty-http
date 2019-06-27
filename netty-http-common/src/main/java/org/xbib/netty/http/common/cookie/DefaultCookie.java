package org.xbib.netty.http.common.cookie;

import java.util.Objects;

/**
 * The default {@link Cookie} implementation.
 */
public class DefaultCookie implements Cookie {

    private final String name;

    private String value;

    private boolean wrap;

    private String domain;

    private String path;

    private long maxAge = Long.MIN_VALUE;

    private boolean secure;

    private boolean httpOnly;

    private String sameSite;

    /**
     * Creates a new cookie with the specified name and value.
     * @param name name
     * @param value value
     */
    public DefaultCookie(String name, String value) {
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }
        setValue(value);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public boolean wrap() {
        return wrap;
    }

    @Override
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    @Override
    public String domain() {
        return domain;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = CookieUtil.validateAttributeValue("domain", domain);
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public void setPath(String path) {
        this.path = CookieUtil.validateAttributeValue("path", path);
    }

    @Override
    public long maxAge() {
        return maxAge;
    }

    @Override
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    @Override
    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    @Override
    public String sameSite() {
        return sameSite;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cookie)) {
            return false;
        }
        Cookie that = (Cookie) o;
        if (!name().equals(that.name())) {
            return false;
        }
        if (path() == null) {
            if (that.path() != null) {
                return false;
            }
        } else if (that.path() == null) {
            return false;
        } else if (!path().equals(that.path())) {
            return false;
        }
        if (domain() == null) {
            if (that.domain() != null) {
                return false;
            }
        } else {
            return domain().equalsIgnoreCase(that.domain());
        }
        if (sameSite() == null) {
            return that.sameSite() == null;
        } else if (that.sameSite() == null) {
            return false;
        } else {
            return sameSite().equalsIgnoreCase(that.sameSite());
        }
    }

    @Override
    public int compareTo(Cookie c) {
        int v = name().compareTo(c.name());
        if (v != 0) {
            return v;
        }
        if (path() == null) {
            if (c.path() != null) {
                return -1;
            }
        } else if (c.path() == null) {
            return 1;
        } else {
            v = path().compareTo(c.path());
            if (v != 0) {
                return v;
            }
        }
        if (domain() == null) {
            if (c.domain() != null) {
                return -1;
            }
        } else if (c.domain() == null) {
            return 1;
        } else {
            v = domain().compareToIgnoreCase(c.domain());
            return v;
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(name()).append('=').append(value());
        if (domain() != null) {
            buf.append(", domain=").append(domain());
        }
        if (path() != null) {
            buf.append(", path=").append(path());
        }
        if (maxAge() >= 0) {
            buf.append(", maxAge=").append(maxAge()).append('s');
        }
        if (isSecure()) {
            buf.append(", secure");
        }
        if (isHttpOnly()) {
            buf.append(", HTTPOnly");
        }
        if (sameSite() != null) {
            buf.append(", SameSite=").append(sameSite());
        }
        return buf.toString();
    }
}
