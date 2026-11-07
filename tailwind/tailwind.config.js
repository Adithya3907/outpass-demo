module.exports = {
  content: [
    '../src/main/resources/templates/**/*.html',
    '../src/main/resources/static/js/**/*.js',
    '../src/main/java/**/*.java'
  ],
  theme: {
    extend: {
      colors: { brand: { DEFAULT: '#1e3a8a', dark: '#172554', light: '#eff6ff' } }
    }
  }
};
