#version 150

uniform sampler2D Sampler0;
uniform vec2 u_resolution;
uniform vec2 u_targetScreen;
uniform float u_radius;
uniform float u_intensity;
uniform float u_time;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution.xy;
    vec2 centered = gl_FragCoord.xy - u_targetScreen;
    float dist = length(centered);

    // 只影响目标点周围圆形区域，避免全屏扭曲
    if (dist > u_radius) {
        fragColor = texture(Sampler0, uv);
        return;
    }

    float t = dist / u_radius;

    // 径向扭曲：越靠近中心，采样越向中心偏移
    float lensFactor = (1.0 - t) * u_intensity * 0.35;
    vec2 targetUv = u_targetScreen / u_resolution.xy;
    vec2 texC = mix(uv, targetUv, lensFactor);

    vec3 bg = texture(Sampler0, texC).rgb;
    vec3 color = bg;

    // 事件视界：半径的 30% 内为黑色
    float eventHorizon = 1.0 - smoothstep(0.0, 0.30, t);
    color = mix(color, vec3(0.0), eventHorizon);

    // 吸积盘光环：在 30% ~ 55% 之间
    float ring = smoothstep(0.30, 0.38, t) * (1.0 - smoothstep(0.45, 0.55, t));
    if (ring > 0.0) {
        float angle = atan(centered.y, centered.x) + u_time * 1.5;
        float flicker = 0.7 + 0.3 * noise(vec2(angle * 2.0, t * 5.0));
        vec3 ringColor = vec3(1.0, 0.45, 0.05) * flicker * u_intensity;
        color = mix(color, ringColor, ring * 0.8);
    }

    // 外部光晕：55% ~ 100%
    float glow = smoothstep(0.55, 1.0, t) * (1.0 - t);
    vec3 glowColor = vec3(0.6, 0.15, 0.0) * glow * u_intensity;
    color += glowColor;

    fragColor = vec4(color, 1.0);
}
