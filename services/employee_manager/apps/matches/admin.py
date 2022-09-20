from django.contrib import admin

from .models import Match


class MatchAdmin(admin.ModelAdmin):
    list_display = ('first_employee', 'second_employee', 'day',)


admin.site.register(Match, MatchAdmin)
